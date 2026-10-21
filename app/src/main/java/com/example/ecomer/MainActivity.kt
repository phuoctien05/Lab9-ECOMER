package com.example.ecomer

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.os.Bundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    EcomerApp()
                }
            }
        }
    }
}

@Composable
fun EcomerApp() {
    val auth = remember { FirebaseAuth.getInstance() }
    var user by remember { mutableStateOf(auth.currentUser) }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener {
            user = it.currentUser
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    if (user == null) {
        LoginScreen(auth)
    } else {
        ProductScreen(auth, user?.email.orEmpty())
    }
}

@Composable
fun LoginScreen(auth: FirebaseAuth) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Đăng nhập để quản lý sản phẩm.") }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "ECOMER",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        Text("Đăng nhập / Đăng ký tài khoản")

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Mật khẩu (ít nhất 6 ký tự)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                val e = email.trim()
                if (e.isBlank() || password.isBlank()) {
                    status = "Vui lòng nhập email và mật khẩu."
                } else {
                    status = "Đang đăng nhập..."
                    auth.signInWithEmailAndPassword(e, password)
                        .addOnSuccessListener {
                            status = "Đăng nhập thành công."
                        }
                        .addOnFailureListener {
                            status = "Đăng nhập thất bại: ${it.localizedMessage}"
                        }
                }
            }
        ) {
            Text("ĐĂNG NHẬP")
        }

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                val e = email.trim()
                if (e.isBlank() || password.length < 6) {
                    status = "Nhập email hợp lệ và mật khẩu từ 6 ký tự."
                } else {
                    status = "Đang tạo tài khoản..."
                    auth.createUserWithEmailAndPassword(e, password)
                        .addOnSuccessListener {
                            status = "Đăng ký thành công."
                        }
                        .addOnFailureListener {
                            status = "Đăng ký thất bại: ${it.localizedMessage}"
                        }
                }
            }
        ) {
            Text("ĐĂNG KÝ TÀI KHOẢN")
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium
        ) {
            Text("ℹ️ $status", modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium)
        }
    }


}

@Composable
fun ProductScreen(auth: FirebaseAuth, email: String) {
    val db = remember { FirebaseFirestore.getInstance() }
    val products = remember { mutableStateListOf<Product>() }

    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var selectedImageUrl by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Đang tải dữ liệu...") }
    var saving by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Product?>(null) }

    DisposableEffect(db) {
        val registration = db.collection("products")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    status = "Lỗi đọc dữ liệu: ${error.localizedMessage}"
                } else {
                    products.clear()
                    snapshot?.documents?.forEach { document ->
                        document.toObject(Product::class.java)?.let { product ->
                            product.id = document.id
                            products.add(product)
                        }
                    }
                    status = "Đã tải ${products.size} sản phẩm."
                }
            }

        onDispose { registration.remove() }
    }

    fun clearForm() {
        name = ""
        category = ""
        price = ""
        selectedId = null
        selectedImageUrl = ""
    }

    fun saveProduct() {
        val parsedPrice = price.trim().toDoubleOrNull()
        val imageUrl = selectedImageUrl.trim()

        when {
            name.isBlank() -> {
                status = "Vui lòng nhập tên sản phẩm."
                return
            }
            category.isBlank() -> {
                status = "Vui lòng nhập loại sản phẩm."
                return
            }
            parsedPrice == null || parsedPrice < 0 -> {
                status = "Giá phải là số không âm."
                return
            }
            imageUrl.isNotBlank() &&
                !(imageUrl.startsWith("https://") || imageUrl.startsWith("http://")) -> {
                status = "URL ảnh phải bắt đầu bằng http:// hoặc https://."
                return
            }
            auth.currentUser == null -> {
                status = "Vui lòng đăng nhập lại."
                return
            }
            saving -> return
        }

        saving = true
        status = "Đang lưu sản phẩm..."

        val wasEditing = selectedId != null
        val productId = selectedId ?: db.collection("products").document().id

        val product = Product(
            id = productId,
            name = name.trim(),
            category = category.trim(),
            price = parsedPrice ?: return,
            image = imageUrl
        )

        db.collection("products")
            .document(productId)
            .set(product)
            .addOnSuccessListener {
                saving = false
                status = if (wasEditing) {
                    "Cập nhật sản phẩm thành công!"
                } else {
                    "Thêm sản phẩm thành công!"
                }
                clearForm()
            }
            .addOnFailureListener { exception ->
                saving = false
                status = "Lưu Firestore thất bại: ${exception.localizedMessage}"
            }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("ECOMER", style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                Text("QUẢN LÝ SẢN PHẨM", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                Text("Xin chào: $email", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { auth.signOut() }) { Text("↪ ĐĂNG XUẤT") }
            }
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Tên sản phẩm") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = category,
            onValueChange = { category = it },
            label = { Text("Loại sản phẩm") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = price,
            onValueChange = { price = it },
            label = { Text("Giá sản phẩm (VNĐ)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = selectedImageUrl,
            onValueChange = { selectedImageUrl = it },
            label = { Text("URL ảnh sản phẩm (không bắt buộc)") },
            placeholder = { Text("https://example.com/image.jpg") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        if (selectedImageUrl.isNotBlank()) {
            AsyncImage(
                model = selectedImageUrl.trim(),
                contentDescription = "Ảnh sản phẩm đang nhập",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentScale = ContentScale.Fit
            )
        } else {
            Text("Chưa nhập URL ảnh. Bạn vẫn có thể lưu sản phẩm không có ảnh.")
        }

        Text(
            if (selectedId == null) "➕ THÔNG TIN SẢN PHẨM" else "✏️ ĐANG CHỈNH SỬA SẢN PHẨM",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = { saveProduct() },
            enabled = !saving
        ) {
            Text(when {
                saving -> "ĐANG LƯU..."
                selectedId == null -> "➕ THÊM SẢN PHẨM"
                else -> "💾 LƯU CẬP NHẬT"
            })
        }

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                clearForm()
                status = "Đã làm mới biểu mẫu."
            },
            enabled = !saving
        ) {
            Text("↻ LÀM MỚI BIỂU MẪU")
        }

        Divider()

        Text(
            "DANH SÁCH SẢN PHẨM (${products.size})",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        if (products.isEmpty()) {
            Text("Chưa có sản phẩm hoặc chưa tải được dữ liệu.")
        }

        products.forEach { product ->
            Card(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (product.image.isNotBlank()) {
                        AsyncImage(
                            model = product.image,
                            contentDescription = "Ảnh ${product.name}",
                            modifier = Modifier.size(104.dp),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Surface(
                            modifier = Modifier.size(104.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            androidx.compose.foundation.layout.Box(
                                contentAlignment = Alignment.Center
                            ) { Text("Ảnh", style = MaterialTheme.typography.bodyMedium) }
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            product.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(product.category, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "${"%,.0f".format(product.price)} VNĐ",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(
                            onClick = {
                                selectedId = product.id
                                name = product.name
                                category = product.category
                                price = product.price.toString()
                                selectedImageUrl = product.image
                                status = "Đang sửa: ${product.name}"
                            }
                        ) { Text("✏️") }

                        OutlinedButton(onClick = { pendingDelete = product }) {
                            Text("🗑️")
                        }
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium
        ) {
            Text("ℹ️ $status", modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium)
        }
    }

    pendingDelete?.let { product ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Xác nhận xóa") },
            text = { Text("Bạn có chắc muốn xóa sản phẩm \"${product.name}\" không? Thao tác này không thể hoàn tác.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    db.collection("products").document(product.id).delete()
                        .addOnSuccessListener {
                            if (selectedId == product.id) clearForm()
                            status = "Đã xóa sản phẩm: ${product.name}"
                        }
                        .addOnFailureListener {
                            status = "Xóa thất bại: ${it.localizedMessage}"
                        }
                }) { Text("XÓA") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("HỦY") }
            }
        )
    }
}

package com.example.lab7crudfirebase

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    CourseScreen()
                }
            }
        }
    }
}

@Composable
fun CourseScreen() {
    val db = remember { FirebaseFirestore.getInstance() }
    val courses = remember { mutableStateListOf<Course>() }

    var name by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("Đang tải dữ liệu...") }

    DisposableEffect(db) {
        val listener = db.collection("Courses")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    status = "Lỗi đọc: ${error.message}"
                } else {
                    courses.clear()
                    snapshot?.documents?.forEach { doc ->
                        doc.toObject(Course::class.java)?.let {
                            it.courseID = doc.id
                            courses.add(it)
                        }
                    }
                    status = "Đã tải ${courses.size} khóa học."
                }
            }
        onDispose { listener.remove() }
    }

    fun clearForm() {
        name = ""
        duration = ""
        description = ""
        selectedId = null
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("LAB 7 - CRUD FIREBASE", style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold)
        Text("Quản lý khóa học bằng Cloud Firestore")

        OutlinedTextField(name, { name = it }, label = { Text("Tên khóa học") },
            modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(duration, { duration = it },
            label = { Text("Thời lượng") }, modifier = Modifier.fillMaxWidth(),
            singleLine = true)
        OutlinedTextField(description, { description = it },
            label = { Text("Mô tả khóa học") }, modifier = Modifier.fillMaxWidth(),
            minLines = 2)

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (name.isBlank() || duration.isBlank() || description.isBlank()) {
                    status = "Hãy nhập đủ 3 trường."
                } else {
                    val id = selectedId ?: UUID.randomUUID().toString()
                    val course = Course(id, name.trim(), duration.trim(), description.trim())
                    db.collection("Courses").document(id).set(course)
                        .addOnSuccessListener {
                            status = if (selectedId == null) "Thêm thành công!"
                                     else "Cập nhật thành công!"
                            clearForm()
                        }
                        .addOnFailureListener { status = "Lưu lỗi: ${it.message}" }
                }
            }
        ) {
            Text(if (selectedId == null) "THÊM KHÓA HỌC" else "LƯU CẬP NHẬT")
        }

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = { clearForm(); status = "Đã làm mới biểu mẫu." }
        ) { Text("Làm mới") }

        Divider()
        Text("DANH SÁCH (${courses.size})", style = MaterialTheme.typography.titleLarge)

        if (courses.isEmpty()) Text("Chưa có khóa học.")

        courses.forEach { course ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(course.courseName, fontWeight = FontWeight.Bold)
                    Text("ID: ${course.courseID}")
                    Text("Thời lượng: ${course.courseDuration}")
                    Text("Mô tả: ${course.courseDescription}")

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            selectedId = course.courseID
                            name = course.courseName
                            duration = course.courseDuration
                            description = course.courseDescription
                            status = "Đang sửa ${course.courseName}"
                        }) { Text("SỬA") }

                        TextButton(onClick = {
                            db.collection("Courses").document(course.courseID).delete()
                                .addOnSuccessListener {
                                    if (selectedId == course.courseID) clearForm()
                                    status = "Xóa thành công!"
                                }
                                .addOnFailureListener { status = "Xóa lỗi: ${it.message}" }
                        }) { Text("XÓA") }
                    }
                }
            }
        }

        Text("Trạng thái: $status")
    }
}


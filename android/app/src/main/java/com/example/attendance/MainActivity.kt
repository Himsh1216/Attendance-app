package com.example.attendance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.example.attendance.ui.InstructorScreen
import com.example.attendance.ui.StudentScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var tab by remember { mutableStateOf(0) }
                var classId by remember { mutableStateOf("ME101") }
                var studentId by remember { mutableStateOf("21CS02010") } // TODO: derive from auth

                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab==0, onClick={tab=0}, text={ Text("Student") })
                    Tab(selected = tab==1, onClick={tab=1}, text={ Text("Instructor") })
                }
                if (tab == 0) StudentScreen(classId = classId, studentId = studentId)
                else InstructorScreen(classId = classId)
            }
        }
    }
}

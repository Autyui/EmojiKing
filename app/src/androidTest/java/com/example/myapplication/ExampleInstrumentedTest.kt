package com.example.myapplication

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

/** 在 Android 设备上运行的仪器测试示例。 */
// 类作用：定义 ExampleInstrumentedTest，承载所在模块的主要职责。
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
// 方法作用：处理 useAppContext 对应的输入并返回或更新相关结果（useAppContext）。
    @Test
    fun useAppContext() {
        // 取得被测应用的上下文并验证其包名。
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.example.myapplication", appContext.packageName)
    }
}
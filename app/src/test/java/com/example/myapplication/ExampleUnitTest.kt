package com.example.myapplication

import org.junit.Test

import org.junit.Assert.*

/** 在开发机上运行的本地单元测试示例。 */
// 类作用：定义 ExampleUnitTest，承载所在模块的主要职责。
class ExampleUnitTest {
// 方法作用：向界面或业务集合中添加新的元素（addition_isCorrect）。
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }
}
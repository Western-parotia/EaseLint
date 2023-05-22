package com.zsw.devopshsyq.data.res

/**
 * @describe:
 * @author: ljj
 * @creattime: 2022/9/21 11:41
 *
 */
data class TestKotlinRes(
    val name: String = "jack",
    val age: Int = 1,
    val list: ArrayList<String>?
) {

    inner class aaaRes @JvmOverloads constructor(

        val xxxx: Int = 1
    )


}

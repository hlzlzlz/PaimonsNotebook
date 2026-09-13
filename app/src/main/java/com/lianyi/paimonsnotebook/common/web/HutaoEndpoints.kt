package com.lianyi.paimonsnotebook.common.web

import com.lianyi.paimonsnotebook.common.core.enviroment.CoreEnvironment

/*
*
* */
object HutaoEndpoints {
    //原官方元数据服务(api.snapgenshin.com)已随Snap.Hutao项目终止而关停,元数据改用社区镜像仓库
    private const val ApiSnapMetadata =
        "https://cdn.jsdelivr.net/gh/SnapHutaoRemasteringProject/Snap.Metadata@master"

    //社区接管的静态资源服务(重制版胡桃工具箱),原api.snapgenshin.com/static已关停
    //api.snaphutaorp.org/static会对static.snaphutaorp.org做302跳转,此处直连最终图床
    const val ApiSnapGenshinStaticRaw = "https://static.snaphutaorp.org/static/raw"
    private const val ApiSnapGenshinStaticZip = "https://static.snaphutaorp.org/static/zip"

    //请求元数据时的header
    val Headers by lazy {
        okhttp3.Headers.Builder()
            .add("User-Agent", CoreEnvironment.PaimonsNotebookUA)
            .build()
    }

    /// <summary>
    /// 胡桃元数据2文件
    /// </summary>
    /// <param name="locale">语言</param>
    /// <param name="fileName">文件名称</param>
    /// <returns>路径</returns>
    fun metadata(locale: String, fileName: String) =
        "${ApiSnapMetadata}/Genshin/${locale}/${fileName}"

    /// <summary>
    /// 元数据下载源列表,主源失败时可依次尝试备用源
    /// </summary>
    fun metadataSources(locale: String, fileName: String) = listOf(
        "${ApiSnapMetadata}/Genshin/${locale}/${fileName}",
        "https://fastly.jsdelivr.net/gh/SnapHutaoRemasteringProject/Snap.Metadata@master/Genshin/${locale}/${fileName}"
    )

    /// <summary>
    /// 图片资源
    /// </summary>
    /// <param name="category">分类</param>
    /// <param name="fileName">文件名称 包括后缀</param>
    /// <returns>路径</returns>
    fun staticRaw(category: String, fileName: String) =
        "${ApiSnapGenshinStaticRaw}/${category}/${fileName}"

    fun staticZip(fileName: String) = "${ApiSnapGenshinStaticZip}/${fileName}.zip"
}

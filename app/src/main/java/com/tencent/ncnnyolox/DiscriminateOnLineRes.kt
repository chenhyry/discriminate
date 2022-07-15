package com.tencent.ncnnyolox

import java.math.BigDecimal

data class DiscriminateOnLineRes(
    var message: String? = null,
    var data: List<Discriminate>? = null
)

class Discriminate(
    var m_x: BigDecimal? = null,
    var m_y: BigDecimal? = null,
    var left_top_x: BigDecimal? = null,
    var left_top_y: BigDecimal? = null,
    var right_bottom_x: BigDecimal? = null,
    var right_bottom_y: BigDecimal? = null,
    var score: BigDecimal? = null,
    var skus: List<SkuInfo>? = null
)

class SkuInfo(
    var sys_code: String? = null,
    var id_code: String? = null,
    var sku_id: Long,
    var sku_name: String? = null
)
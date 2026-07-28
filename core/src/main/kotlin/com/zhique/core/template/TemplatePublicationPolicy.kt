package com.zhique.core.template

enum class TemplateStatus {
    Available,
    Experimental,
    Hidden
}

data class TemplatePublication(
    val id: String,
    val status: TemplateStatus
)

object TemplatePublicationPolicy {
    fun visible(templates: List<TemplatePublication>): List<TemplatePublication> =
        templates.filter { it.status != TemplateStatus.Hidden }
}

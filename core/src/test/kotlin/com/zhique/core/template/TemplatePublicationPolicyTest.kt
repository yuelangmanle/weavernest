package com.zhique.core.template

import kotlin.test.Test
import kotlin.test.assertEquals

class TemplatePublicationPolicyTest {
    @Test
    fun `hidden templates are excluded while available and experimental templates remain visible`() {
        val visible = TemplatePublicationPolicy.visible(
            listOf(
                TemplatePublication("offline-form", TemplateStatus.Available),
                TemplatePublication("ble-lab", TemplateStatus.Experimental),
                TemplatePublication("camera-placeholder", TemplateStatus.Hidden)
            )
        )

        assertEquals(listOf("offline-form", "ble-lab"), visible.map(TemplatePublication::id))
    }
}

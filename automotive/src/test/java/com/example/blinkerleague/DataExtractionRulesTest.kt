package com.example.blinkerleague

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
class DataExtractionRulesTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()

    /** data_extraction_rules.xml에서 section 안의 `exclude`(path=".")가 가리키는 domain을 모은다 */
    private fun excludedDomains(section: String): Set<String> {
        val parser = app.resources.getXml(R.xml.data_extraction_rules)
        val domains = mutableSetOf<String>()
        var inSection = false
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when {
                    parser.name == section -> inSection = true
                    inSection && parser.name == "exclude" &&
                        parser.getAttributeValue(null, "path") == "." ->
                        parser.getAttributeValue(null, "domain")?.let { domains += it }
                }
                XmlPullParser.END_TAG -> if (parser.name == section) inSection = false
            }
        }
        return domains
    }

    @Test
    fun `기기 간 전송 규칙은 앱 데이터 영역을 모두 제외한다`() {
        // given
        // Keystore로 암호화한 토큰(auth) · 기준값 캐시(config) · 운행 DB가 다른 기기로 옮겨지면 안 됨

        // when
        val excluded = excludedDomains("device-transfer")

        // then
        assertEquals(ALL_DOMAINS, excluded)
    }

    @Test
    fun `클라우드 백업 규칙도 앱 데이터 영역을 모두 제외한다`() {
        // given
        // allowBackup=false여도 나중에 바뀔 때를 대비해 규칙에서도 제외

        // when
        val excluded = excludedDomains("cloud-backup")

        // then
        assertEquals(ALL_DOMAINS, excluded)
    }

    private companion object {
        val ALL_DOMAINS = setOf(
            "root", "file", "database", "sharedpref", "external",
            "device_root", "device_file", "device_database", "device_sharedpref",
        )
    }
}

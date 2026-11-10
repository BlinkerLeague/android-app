package com.example.blinkerleague

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.w3c.dom.Element
import org.xmlpull.v1.XmlPullParser
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

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

    @Test
    fun `매니페스트가 백업·전송 규칙을 연결하고 백업을 끈다`() {
        // given
        // tools:ignore="DataExtractionRules" 때문에 연결이 지워져도 lint가 잡지 못하므로 매니페스트 원본을 직접 읽음
        val manifest = listOf(
            File("src/main/AndroidManifest.xml"),
            File("automotive/src/main/AndroidManifest.xml"),
        ).firstOrNull { it.isFile }
            ?: throw AssertionError("src/main/AndroidManifest.xml을 찾지 못함 (작업 디렉터리: ${File("").absolutePath})")

        // when
        val application = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(manifest)
            .getElementsByTagName("application")
            .item(0) as Element

        // then
        assertEquals(
            "@xml/data_extraction_rules",
            application.getAttributeNS(ANDROID_NS, "dataExtractionRules"),
        )
        assertEquals("false", application.getAttributeNS(ANDROID_NS, "allowBackup"))
    }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
        val ALL_DOMAINS = setOf(
            "root", "file", "database", "sharedpref", "external",
            "device_root", "device_file", "device_database", "device_sharedpref",
        )
    }
}

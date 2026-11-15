package com.example.blinkerleague.data.permission

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.blinkerleague.domain.model.PermissionStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class PermissionRepositoryTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `요청 전에는 미요청, 거부하면 거부, 밖에서 허용하면 refresh로 반영`() {
        val repository = PermissionRepository(app)
        val speed = repository.status(RuntimePermission.SPEED)
        assertEquals(PermissionStatus.NOT_REQUESTED, speed.value)

        repository.onRequestResult(RuntimePermission.SPEED) // 사용자가 거부
        assertEquals(PermissionStatus.DENIED, speed.value)

        shadowOf(app).grantPermissions(RuntimePermission.SPEED.manifestName) // 설정에서 허용
        assertEquals(PermissionStatus.DENIED, speed.value)
        repository.refresh()
        assertEquals(PermissionStatus.GRANTED, speed.value)
        assertEquals(PermissionStatus.NOT_REQUESTED, repository.status(RuntimePermission.LOCATION).value)
    }

    @Test
    fun `요청한 적 있음은 앱을 다시 켜도 유지됨`() {
        PermissionRepository(app).onRequestResult(RuntimePermission.LOCATION)
        assertEquals(PermissionStatus.DENIED, PermissionRepository(app).status(RuntimePermission.LOCATION).value)
    }

    @Test
    fun `상태 결정 규칙`() {
        assertEquals(PermissionStatus.GRANTED, PermissionStatus.of(granted = true, requestedBefore = false))
        assertEquals(PermissionStatus.DENIED, PermissionStatus.of(granted = false, requestedBefore = true))
        assertEquals(PermissionStatus.NOT_REQUESTED, PermissionStatus.of(granted = false, requestedBefore = false))
    }
}

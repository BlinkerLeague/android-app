package com.example.blinkerleague.data.scenario

import com.example.blinkerleague.domain.model.Gear
import com.example.blinkerleague.domain.model.TurnSignalState

/** plan.md 2단계의 재생용 시나리오. 테스트와 에뮬레이터 시연에 같이 씀. */
object Scenarios {

    /** 좌회전 3초 전에 왼쪽 깜빡이를 켬 → 성공 1회 */
    val leftTurnWithSignal = scenario("좌회전 + 깜빡이") {
        start()
        straight(seconds = 20, speedKmh = 40f)
        signal(TurnSignalState.LEFT)
        straight(seconds = 3, speedKmh = 20f)
        turn(angleDeg = -90f, seconds = 6, speedKmh = 20f)
        signal(TurnSignalState.OFF)
        straight(seconds = 20, speedKmh = 40f)
        end()
    }

    /** 우회전하면서 깜빡이를 켜지 않음 → 실패 1회 */
    val rightTurnWithoutSignal = scenario("우회전 + 깜빡이 없음") {
        start()
        straight(seconds = 20, speedKmh = 40f)
        straight(seconds = 3, speedKmh = 20f)
        turn(angleDeg = 90f, seconds = 5, speedKmh = 20f)
        straight(seconds = 20, speedKmh = 40f)
        end()
    }

    /** 진입 우회전 → 반시계 방향 회전 → 진출 우회전. 좌우가 섞인 연속 회전이라 판정에서 제외 → 회전 0회 */
    val roundabout = scenario("회전교차로") {
        start()
        straight(seconds = 20, speedKmh = 40f)
        turn(angleDeg = 70f, seconds = 4, speedKmh = 20f)
        turn(angleDeg = -230f, seconds = 12, speedKmh = 20f)
        turn(angleDeg = 70f, seconds = 4, speedKmh = 20f)
        straight(seconds = 20, speedKmh = 40f)
        end()
    }

    /** 주차장 안에서 저속(5km/h 미만)으로 핸들을 크게 꺾고 후진(R) → 회전 0회 */
    val parkingLot = scenario("주차장 이동") {
        start()
        straight(seconds = 10, speedKmh = 4f)
        turn(angleDeg = 90f, seconds = 4, speedKmh = 3f)
        straight(seconds = 8, speedKmh = 4f)
        turn(angleDeg = -90f, seconds = 4, speedKmh = 3f)
        stop(seconds = 2)
        gear(Gear.R)
        turn(angleDeg = 90f, seconds = 5, speedKmh = 2f)
        stop(seconds = 2)
        end()
    }

    /** 시연 영상 흐름: 좌회전 깜빡이 성공 → 우회전 실패 → 운행 종료. 점수 대상이 되게 3km 넘게 달림(서버 D10) */
    val demo = scenario("시연: 좌회전 성공 → 우회전 실패") {
        start()
        straight(seconds = 60, speedKmh = 90f)
        signal(TurnSignalState.LEFT)
        straight(seconds = 4, speedKmh = 25f)
        turn(angleDeg = -90f, seconds = 6, speedKmh = 20f)
        signal(TurnSignalState.OFF)
        straight(seconds = 60, speedKmh = 90f)
        straight(seconds = 4, speedKmh = 25f)
        turn(angleDeg = 90f, seconds = 5, speedKmh = 20f)
        straight(seconds = 30, speedKmh = 90f)
        end()
    }

    /** 3km 이상, 회전 3번 모두 깜빡이 성공 → 오늘의 미션 달성 확인용 */
    val missionAchieved = scenario("미션 달성: 회전 3번 모두 성공") {
        start()
        straight(seconds = 40, speedKmh = 90f)
        listOf(TurnSignalState.LEFT to -90f, TurnSignalState.RIGHT to 90f, TurnSignalState.LEFT to -90f).forEach { (signal, angle) ->
            signal(signal)
            straight(seconds = 3, speedKmh = 25f)
            turn(angleDeg = angle, seconds = 5, speedKmh = 20f)
            signal(TurnSignalState.OFF)
            straight(seconds = 30, speedKmh = 90f)
        }
        end()
    }

    val all = listOf(leftTurnWithSignal, rightTurnWithoutSignal, roundabout, parkingLot, demo, missionAchieved)
}

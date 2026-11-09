package io.github.romulomga.corekit

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * O relógio de produção. `Clock.systemDefaultZone()` guarda o fuso de quando foi criado; este lê o do aparelho a cada
 * uso, para quem viaja com o processo vivo. Injete um `Clock` só no app inteiro e tire o fuso sempre dele.
 */
public object SystemZoneClock : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()

    override fun withZone(zone: ZoneId): Clock = system(zone)

    override fun instant(): Instant = Instant.now()
}

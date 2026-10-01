
plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

tasks.withType<Test>().configureEach {
    useJUnit()
}

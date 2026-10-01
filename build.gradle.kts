
plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

tasks.withType<Test>().configureEach {
    useJUnit()
    inputs.dir("resourcepacks/Modernity-BackpackEnhance")
}

val exportDefaultResourcePack by tasks.registering(Sync::class) {
    group = "build"
    description = "Exports the default reference pack from the mod's textures and colors."
    into(layout.buildDirectory.dir("resourcepacks/BackpackEnhance-Default"))
    from("resourcepacks/BackpackEnhance-Default")
    from("src/main/resources/assets/backpackenhance") {
        into("assets/backpackenhance")
        include("textures/gui/overlay/**", "gui/overlay.properties")
    }
}

val resourcePackVersion = providers.gradleProperty("resourcePackVersion").orElse(project.version.toString())

val modernityResourcePackZip by tasks.registering(Zip::class) {
    group = "build"
    description = "Packages the Modernity adapter resource pack."
    archiveBaseName.set("Modernity-BackpackEnhance")
    archiveVersion.set(resourcePackVersion)
    destinationDirectory.set(layout.buildDirectory.dir("resourcepacks"))
    from("resourcepacks/Modernity-BackpackEnhance")
    include("pack.mcmeta", "pack.png", "assets/**", "README*", "LICENSE*")
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

val packageResourcePacks by tasks.registering {
    group = "build"
    description = "Builds the Modernity resource pack ZIP for distribution."
    dependsOn(modernityResourcePackZip)
}

tasks.assemble {
    dependsOn(packageResourcePacks)
}

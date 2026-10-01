# Development and release workflow / 开发与发布流程

## 中文

1. 从最新的 `main` 创建功能或修复分支，在分支上开发并提交。
2. 推送分支后，创建以 `main` 为目标的 Pull Request（PR）。PR 的 `CI / Build and test` 必须通过；检查覆盖完整构建、JUnit 测试、Spotless 和 Checkstyle。
3. 通过 PR 合并至 `main`。主分支保护要求 PR 和成功的 `Build and test` 检查，并要求分支与目标分支保持同步。
4. 合并后会再次触发 `main` 的 CI。等待准备发布的那个提交验证成功。
5. 在已通过主分支 CI 的提交上创建版本标签，例如 `0.3.1`，并推送该标签。标签使用不带 `v` 前缀的版本号。
6. 标签触发 `Release tagged build`。发布前会检查提交已进入 `main`，且该提交最新一次主分支 CI 成功，随后生成正式、开发和源码 JAR。`Publish Modernity resource pack` 在 JAR 发布任务完成后，将 Modernity 适配包 ZIP 上传到同一个 Release，文件名使用该标签的版本号。默认材质由模组内置提供。
7. 确认 GitHub Release、三个 JAR 和 Modernity 适配包 ZIP 均发布成功后，更新 GTNH-Mod-Hub 中的版本、下载链接及相关功能说明。

发布说明可以随 PR 放入 `.changelogs/<版本号>.md`；省略时由发布工作流生成。已发布版本使用原有标签和产物，后续修复使用新的版本号。

本地验证使用 Gradle wrapper：`./gradlew build --console=plain`；Windows 使用 `./gradlew.bat build --console=plain`。需要格式化时运行 `spotlessJavaApply`。

## English

1. Create a feature or fix branch from the latest `main` and commit the changes there.
2. Push the branch and open a pull request targeting `main`. `CI / Build and test` must pass; it runs the full build, JUnit tests, Spotless and Checkstyle.
3. Merge through the pull request. Branch protection requires a pull request, a successful `Build and test` check and an up-to-date branch.
4. Wait for the post-merge CI run on the intended release commit on `main` to succeed.
5. Create and push a bare version tag, such as `0.3.1`, on that verified commit.
6. The tag triggers `Release tagged build`. Its gate checks that the commit is on `main` and its latest main-branch CI run succeeded before producing the regular, development and sources JARs. After that job completes, `Publish Modernity resource pack` uploads the Modernity ZIP to the same release using the tag as its version. Default textures are bundled with the mod.
7. Verify the published release, all three JARs and the Modernity resource-pack ZIP, then update the version, download links and relevant feature description in GTNH-Mod-Hub.

Release notes may be included in the pull request at `.changelogs/<version>.md`; otherwise the release workflow generates them. Preserve published tags and artifacts, and use a new version for subsequent fixes.

Use the Gradle wrapper for local verification: `./gradlew build --console=plain`, or `./gradlew.bat build --console=plain` on Windows. Run `spotlessJavaApply` when formatting is needed.

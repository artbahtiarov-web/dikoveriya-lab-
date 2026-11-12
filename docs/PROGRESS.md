# Диковерия LAB — журнал автономной разработки

## 2026-11-11: Инициализация

- **Базис:** `main` до начала итерации: `77637f54253e2e7a9f8130c6ec4c56b1eac63121`, versionName `0.6.0-dev`, SQLite версия 7.
- **Проверенный прошлый APK:** GitHub Actions run `43069882670` (success), Android unit tests + release signing + artifact.
- **ТЗ:** Issue #4, аудит Issue #3, ранние требования Issue #1.
- **Шаг 0:** в PR из ветки `chore/pr-ci-roadmap-20261111` добавлены workflow `.github/workflows/validate-pr.yml`, дорожная карта, инварианты архитектуры. На момент создания записи PR CI ещё не проверена.
- **Риск:** отдельного CI на Pull Request ранее не было. Release workflow в `main` выполняет устаревший шаг apply_v050; позже упростить сборку без ZIP-инсталлятора после подтверждения новой PR-валидации.
- **Проверка перед merge:** PR workflow должны реально пройти Python baseline checks, Gradle debug unit tests and debug APK. Изменения инфраструктуры не меняют SQLite, подписывание или прикладные файлы.
- **Следующий шаг:** проверить CI, объединить безопасную инфраструктуру, затем добавить регрессионные тесты и начать исправлять единицы учёта / наблюдения / рецепт при пересадках в Stage 1.

Правило: каждое последующее изменение вносить новой датированной секцией с SHA, PR, CI run, рисками и конкретным next step. Не писать «проверено», пока не подтверждено результатами CI.

## 2026-11-11 19:44 UTC — Stage 1, журнал наблюдений (PR #6)

- **База:** main `9b50317f35cd09a981315bb9b99f58552d8ddaab`, release CI `43082233478` success; открытых PR до итерации не было.
- **Ветка:** `fix/growth-observation-feedback-20261111`, HEAD `4bd38153667679106f561546e968c632f8c9a688`; PR https://github.com/artbahtiarov-web/dikoveriya-lab-/pull/6.
- **Исправлено:** разрешены наблюдения с одной текстовой заметкой (пустые числовые поля остаются SQL NULL, не нулём); после успешной записи появляется явное подтверждение; записи без чисел показывают заметку без пустого разделителя.
- **Проверка:** вручную сверены пути записи `LabFlowStore.addGrowthSample` и Compose `GrowthDialog`; изменения не затрагивают SQLite-схему, релизную подпись или историю. CI нового PR на момент записи **не подтверждён** — объединение запрещено до зелёных `testDebugUnitTest` и `assembleDebug`.
- **Оставшиеся риски:** стартовая запись по-прежнему считается наблюдением; два параллельных журнала; нет подтверждённых instrumentation тестов; UX сохранения на реальном устройстве не проверен.
- **Следующий шаг:** проверить Actions PR #6; при зелёном CI и повторной логической проверке объединить безопасное изменение, проверить подписанный релиз; затем отдельно исправлять исходные измерения и разрозненные журналы.

## 2026-11-11 21:48 UTC — Stage 1, достоверность аналитики сред (PR #7)

- **База:** main `1546b198255355464fa7623ee927ce06fd9145e8`; PR #6 merged, его PR CI `43090155163` success; релизный run `43097705105` на момент проверки **queued**, успешный подписанный APK нового main **не подтверждён**. Открытых PR до создания #7 не было.
- **Ветка:** `fix/measurement-baseline-analytics-20261111`; PR https://github.com/artbahtiarov-web/dikoveriya-lab-/pull/7, commit до записи журнала `0b58466baaed1d40831ecc17ff9c5622371660d5`.
- **Изменено:** `mediumResults()` теперь считает наблюдения только с числовыми показателями и исключает стартовую запись `Показатели при создании`; текстовые осмотры не увеличивают счётчик. SQL сохраняет смысл `0` как измеренного значения, `NULL` как отсутствующего измерения. Исторические строки не удаляются и не переписываются, последние измеренные количества продолжают отображаться. Добавлен тест `qa/test_medium_observation_count.py`, выполняющий фактический SQL из Kotlin в SQLite и проверяющий стартовую запись, заметку, 0, NULL и последующее измерение. CI теперь выполняет этот тест.
- **Проверка:** логически сверен SQL и исходники; PR CI https://github.com/artbahtiarov-web/dikoveriya-lab-/actions/runs/43106439299 **queued**, сборка/тесты ещё не подтверждены. **Не объединять до успешного CI.** БД остаётся v7; подписывание не менялось.
- **Ограничения:** историческая стартовая запись распознаётся по точной системной заметке; в будущем ввести явное поле типа записи с аддитивной миграцией. Этот PR не исправляет старый журнал `assessments`, `quantity_kind`, перенос `medium_recipe_id` при пересадках и навигацию.
- **Следующий шаг:** дождаться PR CI #7, при ошибке разобрать логи и исправить в том же PR. При зелёном CI и повторном аудите безопасно объединить, проверить релизную подпись. Затем приоритет — типизированные перемещаемые единицы и связь рецептуры у дочерних контейнеров.

## 2026-11-12 00:48 UTC — Stage 1, дочерние контейнеры и рецептуры (PR #8)

- **База:** main `583b6264e312e14222f99a356f57700327c5fecb`, подписанный release run `43122663856` success; открытых PR до итерации нет.
- **Ветка:** `fix/child-recipe-lineage-20261112`; PR https://github.com/artbahtiarov-web/dikoveriya-lab-/pull/8; начальный head `7090a0998b1df435610dd9b483f74eb9b18f88f0`.
- **Исправлено:** split и merge назначают дочернему контейнеру рецептуру и записывают историю в рамках существующей SQLite-транзакции. Выбранная партия определяет рецепт; без партии при split наследуется рецепт источника; при merge наследуется только общий для всех источников рецепт, иначе остаётся NULL. Существующие записи не изменены, SQLite v7 и release signing не тронуты.
- **Тесты:** добавлен `qa/test_daughter_recipe_lineage.py` для защиты ветвей логики; PR CI должен запустить Python unittest, Android unit tests и assembleDebug. **На момент записи результат CI не подтверждён**. Проверить, что `CultureRecord.recipeId` читается из БД корректно, и проверить отсутствие Kotlin compile errors по Actions.
- **Риски:** UI по-прежнему предлагает batch вместо прямого recipe; типизированные количества ещё требуют исправления. Нельзя автоматически угадывать рецепт при смешении разных источников. Реальное Android UI и печать не проверены.
- **Следующий шаг:** проверить PR #8 CI и исправить любые ошибки; после зелёного CI и проверки логики объединить, проверить подписанный release APK; затем перейти к корректному учёту перемещаемого материала и выбору рецепта в операциях.

## 2026-11-12 02:44 UTC — Stage 1, защита типизированных операций (PR pending)

- **База:** main `0fb4801faab79fcd8fe3ecc12253258fe80ae508`, release Actions `43136377073` success; открытых PR перед итерацией нет.
- **Ветка:** `fix/typed-operation-source-20261112`, изменения `LabDatabase.kt`, `LabOperationDialogs.kt`, `qa/test_typed_operation_source.py`.
- **Исправлено:** split/merge отклоняют операции с историческим `quantity_kind="Не уточнено"` до создания `lab_operations`; merge по-прежнему требует одинаковых категорий, а список кандидатов в UI теперь отфильтрован по категории. Кнопка пересадки/объединения отключена для неуточнённого типа; пользователь видит причину. Это защищает от ошибочного списания неизвестных единиц и от смешивания эксплантов/побегов/растений.
- **Ограничение:** пока нет UI для подтверждённого уточнения типа старого контейнера; не менять старые записи автоматически. Отдельный этап должен добавить аудируемое действие уточнения единицы и выбор единицы входа/выхода при пересадке. В этой итерации SQLite-схема и существующие данные не менялись, сертификат неизменен.
- **Проверка:** регрессионные source-level тесты добавлены к обязательному `python3 -m unittest discover -s qa`; PR CI проверит Kotlin компиляцию, unit tests, debug APK. Не объединять до успешного CI. Android instrumentation и UX на устройстве не проверены.
- **Следующий шаг:** проверить CI PR, устранить ошибки, объединить только при зелёной проверке; затем создать безопасный сценарий уточнения исторического типа объектов с записью в аудит и регрессионными тестами.

## 2026-11-12 04:48 UTC — Stage 1, запрет потерь/коррекции без известной единицы (PR pending)

- **База:** main `82a8e234de74cad46b300fb5c112749e60f8c29e`; PR #9 merged; релизный Actions run `43149758994` **success**. Открытых PR перед изменениями нет.
- **Ветка:** `fix/unknown-kind-loss-correction-20261112`.
- **Исправлено:** в `recordLoss` и `correctQuantity` теперь до записи операции проверяется `quantityKind != "Не уточнено"`. До этого защита существовала только для split/merge, но потери и исправление количества могли менять неоднозначные единицы. UI блокирует эти четыре вида действий и показывает причину.
- **Проверки:** добавлен `qa/test_unknown_kind_mutations.py` (проверяет охранные условия и состояние кнопки); в PR CI должны выполняться Python проверки, Android unit tests и assembleDebug. **Результаты нового CI пока не подтверждены; PR не объединять до зелёного CI.**
- **Безопасность:** нет изменения SQLite v7, старых записей, подписывания, backup-формата. Риск: для исторических контейнеров с неизвестной единицей остаётся недоступна корректировка и списание — до разработки отдельного подтверждаемого и аудируемого сценария уточнения типа.
- **Следующий шаг:** проверить PR CI, исправить ошибки, объединить только при успехе; затем реализовать безопасный выбор/уточнение `quantity_kind` с сохранением старого и нового значения в истории, не угадывая тип автоматически.


## 2026-11-12 07:49 UTC — Stage 1, legacy batch edit consistency (PR pending)

- **Base main:** `047b331633bf43e183fd5301671a4e57cc1aa135`. Latest signed release run [43170137934](https://github.com/artbahtiarov-web/dikoveriya-lab-/actions/runs/43170137934) succeeded and published `dikoveriya-lab-signed-release`. No open PR before this change.
- **Branch:** `fix/culture-batch-recipe-sync-20261112`; code commit `14c5d0401ec4fc2f089331b00cf78762513d2244`; test commit `43eb289da75879a5f7d7fc66f80b03e483067622`.
- **Change:** the legacy `updateCulture` path previously changed `medium_batch_id` without changing `medium_recipe_id`, producing contradictory effective medium metadata. On selecting a different non-null batch, its recipe now updates the direct recipe in the same transaction and appends recipe history when the recipe changes. Clearing a batch deliberately preserves the direct recipe; existing records and SQLite v7 are untouched.
- **Checks:** new `qa/test_batch_recipe_edit.py` source regression; PR CI must run Python checks, `:app:testDebugUnitTest`, and `:app:assembleDebug`. Local Android compilation and on-device tests not available. **Do not merge until CI success.**
- **Risk:** legacy edit UI still offers batch rather than direct recipe; no Android instrumentation test of historical database migration. Future work should unify edit forms around recipe selection and explicit user confirmation of changes.
- **Next:** inspect PR CI, fix errors in same PR; merge only after success and low-risk review, verify permanent signed release artifact. Then implement explicit historical quantity-kind clarification with an audit event.


## 2026-11-12 UTC — комплексный пакет 0.6.1 / release candidate

- **Main до пакета:** `bccc9e0f71aacdbc25d8dc87e691cedf125de606` после PR #11; PR CI #11 https://github.com/artbahtiarov-web/dikoveriya-lab-/actions/runs/43178251548 — success.
- **Ветка:** `release/0.6.1-integrated-lab-core-20261112`; предварительный HEAD `f00d555ff0596fb2871ad73f79a2d87d574da473`. Результат будет привязан к PR и новому SHA после последующих исправлений.
- **Изменено:** SQLite 7→8 аддитивно; таблица `culture_audit`, before/after trigger и связь с операцией; явные `baseline` / `observation`; единая хронология `events`+`assessments`+`culture_measurements`; подтверждение неизвестного типа учёта вручную с указанием фактического количества; сохранение nullable измерений; версии рецептур при пересадке/объединении независимо от партии; атомарная смена среды с аудитом; правки копирования сахарозы/агара в новую версию; редактирование контейнера по direct recipe; v4–v8 JSON/ZIP восстановление; атомарный экспорт и проверка целостности; ограничение продаж растениями; исправление отмены через реально существующее поле `reversal_of`. Кандидат `0.6.1-rc1`.
- **Тесты:** добавлен `qa/test_lab_core_v8.py` (реальный SQLite DDL/trigger, rollback, null/zero, FK); обновлены существующие исходные регрессионные тесты. **PR CI и Kotlin compilation пока не подтверждены**; не заявлять, что версия принята.
- **Риски:** физическая миграция пользовательской v7 БД, восстановления архивов v4–v8 и UX на Android не проверены реальными данными/устройством. Риск несовместимости SQLite/Compose остаётся до успешного CI. Пока не объединять в `main`.
- **Ссылки:** пакет: https://github.com/artbahtiarov-web/dikoveriya-lab-/tree/release/0.6.1-integrated-lab-core-20261112 ; критерии: `docs/RELEASE_0_6_1.md`.
- **Следующий конкретный шаг:** открыть PR 0.6.1, дождаться и разобрать CI (Python, `:app:testDebugUnitTest`, `:app:assembleDebug`), исправить ошибки В ЭТОМ PR. После зелёного CI провести миграцию и восстановление на старых БД; лишь после безопасной приёмки менять `0.6.1-rc1` на `0.6.1` и объединять, затем проверять подписанную release-сборку и артефакт.

## 2026-11-12 08:57 UTC — 0.6.1 CI acceptability checkpoint (PR #12)

- **HEAD tested:** `7d54253bf163ed4981e0823b3529f43ca30c9cba`.
- **PR:** https://github.com/artbahtiarov-web/dikoveriya-lab-/pull/12 .
- **CI:** https://github.com/artbahtiarov-web/dikoveriya-lab-/actions/runs/43186872048 — **success** (23 Python/SQLite regression tests; Android `:app:testDebugUnitTest`, `:app:assembleDebug`, PR debug APK artifact `13929246936`). Before this result the first run failed two stale test assertions and they were corrected in the same PR.
- **Last additions:** before/after audit shown in culture history, explicit human reentry of legacy material count, legacy-state snapshots distinguished from actual changes, SQLite v7-shaped migration/recovery test `qa/test_v8_upgrade_restore.py`.
- **Remaining release checks:** `versionName` raised from `0.6.1-rc1` to `0.6.1` in a later commit; must reverify latest SHA's CI and, if merged, signed main release APK/certificate. Real-user v4–v8 ZIP archives and physical Android UX still not exercised; no claim that these were tested.
- **Data-risk rule:** SQLite v8 migration is additive and old tables are preserved; old backups remain supported, but unknown real historical DB variants cannot be independently validated in this session. Only release if new PR CI is successful; record any residual limitation.
- **Next:** check latest PR CI, selectively merge if safe, verify signed APK artifact and label accurately. Then Stage 2 navigation/UX; do not confuse 0.6.1 with 1.0.

## 2026-11-12 09:01 UTC — контроль подписанного выпуска 0.6.1

- **PR:** https://github.com/artbahtiarov-web/dikoveriya-lab-/pull/12 .
- **Проверенный исходный кандидат:** `7d54253bf163ed4981e0823b3529f43ca30c9cba`, PR CI https://github.com/artbahtiarov-web/dikoveriya-lab-/actions/runs/43186872048 **success**: 23 Python/SQLite tests, `:app:testDebugUnitTest`, `:app:assembleDebug`, artifact ID `13929246936`.
- **В выпуск дополнительно внесено:** versionName `0.6.1`; исправление `.github/workflows/build-apk.yml`: удалён опасный шаг v0.5 ZIP patch/automated git commit/push; permissions снижены `contents: write` → `contents: read`. Подписанный APK теперь должен строиться строго из checkout текущего SHA `main`; постоянные подписывающие секреты, сертификат и его SHA-256 НЕ менялись.
- **Ограничения проверки:** новый SHA после этих изменений должен получить свой зелёный PR CI, затем merge в `main` с проверкой фактического подписанного release workflow и наличия APK. Реальные пользовательские резервные копии и физический Android не доступны для тестирования; Python v7→v8 fixture не следует выдавать за реальную восстановленную копию пользователя.
- **Следующий шаг:** проверить последний PR CI; при успехе и приемлемом риске объединить, затем проверить подписанную сборку и артефакт. При провале — логи и исправление в PR #12, без merge.
 

## 2026-11-12 10:17 UTC — реализация комплексного UI 0.7.0 (PR pending)

- **База main:** `c22b9a281f8b02b9ac5ea5e31f7590a55ebf3269` (стабильная v0.6.1 и SQLite v8).
- **Ветка:** `release/0.7.0-botanical-navigation`; SHA фиксируется после завершения PR.
- **Код:** глубокая лесная/латунная/шампань палитра из брендбука, векторная «Арка открытий», ботаническая типографика, главный dashboard и быстрые действия, иерархия Коллекция → Сорт → Контейнер, четыре плитки, полноэкранные Рост/Среда/Операции/История, график измерений по реальным значениям, фильтры и связанный аудит, Back строго по маршруту, saveable scroll/filters/fields и переключение вкладок. Существующие операции/SQLite v8/backup не переписывались.
- **Проверки запланированы:** PR CI Python/JVM/Android debug, ручная приёмка на 360/390/430 dp и fontScale 1.5–1.6 (есть preview definitions, они не равны реальным скриншотным тестам). Подписанный release artifact ещё не подтверждён.
- **Документ:** `docs/RELEASE_0_7_0.md`.
- **Известный риск:** старые динамические multi-destination поля диалога операций ещё требуют runtime проверки сохранения; UI на физическом телефоне пока не тестировался.
- **Следующее действие:** открыть PR, разобрать CI и устранить ошибки компиляции/тестов в PR; merge только при green и безопасной обратимости, затем проверка signed build/артефакта.


## 2026-11-12 10:58 UTC — UI 0.7.0, проверка реальным эмулятором

- **Ветка и PR:** `release/0.7.0-botanical-navigation`, https://github.com/artbahtiarov-web/dikoveriya-lab-/pull/15 .
- **Android CI:** https://github.com/artbahtiarov-web/dikoveriya-lab-/actions/runs/43204009450 — **success**, source/Python/SQLite/JUnit/Compose compile и debug APK.
- **Android emulator:** https://github.com/artbahtiarov-web/dikoveriya-lab-/actions/runs/43204009341 — **success**, instrumented `connectedDebugAndroidTest` для 360dp/150% и 430dp/160% шрифта, проверка KPIs, 4 живых плиток/кликов и границ. Снимки: артефакт 13937481050 (lab07-ui-360-430). 390dp описан только Preview, не запускался как отдельный инструментальный профиль.
- **Ошибки и исправления в одном PR:** `pipefail` был недопустим в POSIX sh emulator-runner; исправлен. `mapSaver` и положение импортов в Kotlin вызывали ошибку компиляции; исправлены. Дополнительно добавлены Saver для списка дочерних контейнеров, вариантов среды, merge и инцидентов; финальный тест компиляции успешен.
- **Версия:** после подтверждения instrumented UI в `app/build.gradle.kts` и source-test изменено `0.7.0-rc1` → `0.7.0`. Требуется финальное CI на последнем SHA, затем merge. Идёт безопасное UI-обновление без изменения SQLite v8, package name, signing.
- **Риски/следующий шаг:** не объявлять полную физическую UX-приёмку или подписанный release APK до фактической проверки. Получить green CI на final SHA; merge PR, проверить signed release workflow и артефакт. 


## 2026-11-12 11:55 UTC — комплексный UX-релиз 0.7.1: функциональный аудит от А до Я

- **База main:** `a83b2359c4b367d33cc8b40c171cb0517c6ab481` (0.7.0). Рабочая ветка `release/0.7.1-unified-workflow-label`; **PR #16:** https://github.com/artbahtiarov-web/dikoveriya-lab-/pull/16.
- **Полная инвентаризация и сценарии:** `docs/UX_AUDIT_0_7_1.md`: разделы, каждая значимая функция, где ранее выполнялась, целевой путь, почему изменение уменьшает клики, где сохранена совместимость; отдельная матрица испытаний.
- **Фактически изменены Kotlin/Compose:** вместо пяти вкладок четыре — Обзор/Коллекция/Среды/Ещё; общий поиск «Сорта/Контейнеры»; единый центр рецептур, необязательных приготовлений, компонентов и CSV; паспорт без старого скрытого `PickOne("Действие")`; быстрые кнопки наблюдения, пересадки, потерь, этикетки, фото, отдельная продажа. При явном действии не отображается повторный выбор «Что произошло». Сорт подставляется при создании контейнера из открытой карточки.
- **Без потери функций:** старые расширенные архивные осмотры отделены от современных типизированных измерений, и их интерфейс сохранён; операции отмены, объединения, инцидентов, исправлений, номенклатура/маточники/эксперименты/склад/задачи/продажи/аналитика/CSV/ZIP остаются доступны. Убраны ложные `onClick={}` в аналитике и продажах; незаверенные «коэффициенты эффективности» старого журнала больше не выдаются за научный вывод; историю не обрезаем по 50 строк.
- **Этикетки:** `LabLabel40.kt` рендерит реально горизонтальный PNG **40×12 мм** (203/300 DPI + pHYs), слева QR для стабильного кода контейнера, справа название растения, код, пассаж, название среды/версия; редактирование подписи и включения среды, PNG через SAF, Share/NIIMBOT через ограниченный FileProvider (`cache/labels`). Нет прямого Bluetooth API и реальный отпечаток NIIMBOT не проверен — это задача приёмки.
- **Данные/сертификат:** `LabDatabase` и SQLite v8 **не изменяли**, backup v4..v8, учётные операции и versioned medium provenance остаются; Android applicationId/keystore/certificate не меняли.
- **Проверки на rc-коде:** https://github.com/artbahtiarov-web/dikoveriya-lab-/actions/runs/43212560529 — success (36 Python проверок, :app:testDebugUnitTest, :app:assembleDebug); https://github.com/artbahtiarov-web/dikoveriya-lab-/actions/runs/43212560462 — success (Android instrumented 360dp/150% и 430dp/160%, QR распознаётся тестом, геометрия PNG, снимки). Поздний пакет исправлений аналитики/истории прошёл Android CI https://github.com/artbahtiarov-web/dikoveriya-lab-/actions/runs/43213752842 — success; его Android UI https://github.com/artbahtiarov-web/dikoveriya-lab-/actions/runs/43213752867 на момент записи ещё запущен. Эти результаты не следует переносить на следующий SHA без проверки.
- **Финальная версия:** `versionName = "0.7.1"`, `versionCode = 70100 + GITHUB_RUN_NUMBER`. После изменения версии нужен **новый окончательный CI**; если failed — чинить в PR #16, **не** объединять в main.
- **Остаточные ограничения:** реальную установку поверх личной базы пользователя, экранные сценарии, термопечать на выбранной модели NIIMBOT и импорт архивов с телефона нельзя воспроизвести по GitHub API. PNG точно содержит физические метаданные, но печатное приложение может изменить масштаб. Свободное перемещение объектов/шрифты/Undo/Redo/шаблоны — отдельный релиз 0.8, **не заявлять** как функции 0.7.1.
- **Следующий строго определённый шаг:** получить и проверить Android CI и два прогона instrumented tests для последнего head, оценить регрессии и объединить PR #16 только при зелёном статусе. Проверить main SHA, подписанную сборку и реальное наличие APK с прежним сертификатом. До этого пользователь не должен устанавливать PR-debug APK вместо подписанного.

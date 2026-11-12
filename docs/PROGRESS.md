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

## 2026-11-12 08:42 UTC — Stage 0, immutable release build

- **Base main:** `bccc9e0f71aacdbc25d8dc87e691cedf125de606`; last signed release run [43182911466](https://github.com/artbahtiarov-web/dikoveriya-lab-/actions/runs/43182911466) success. No open PR at start.
- **Branch:** `chore/immutable-release-source-20261112`; code commit `2a8a3c431463e46f475ffa493cc38e94a6cdf295`.
- **Change:** removed legacy ZIP extraction, Python patch execution, and automatic commit/push to main from release workflow. GitHub token permissions reduced from contents:write to contents:read. Release builds only the checked-out tracked source; baseline validation and full Python regression suite run before Gradle. Existing permanent signing restoration, certificate verification and signed artifact upload remain unchanged.
- **Checks:** source-level workflow review; PR workflow must execute `:app:testDebugUnitTest` and `:app:assembleDebug`. Release validation cannot be claimed until after merge; no local Android SDK compilation. No Kotlin, SQLite, migrations, app ID or signing certificate changes.
- **Risks:** release workflow behavior changes; verify existing baseline validator and Python suite on PR, then signed release build and artifact on main. Do not merge if PR CI fails.
- **Next:** verify PR CI, merge only after green result, confirm signed release artifact; then implement auditable clarification of unknown historical quantity type before further quantity operations.

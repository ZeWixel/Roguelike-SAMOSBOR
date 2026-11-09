# Фото-основы для текстур

Компактные `base_*.png` (64×64 RGB) получены из фотореалистичных изображений,
сгенерированных ИИ, скриптом `prepare.py` (квадратный кроп → бокс-даунсемплинг
→ сглаживание швов для тайлинга). Большие исходники (~1408×768, ~3 МБ каждый)
в репозиторий не кладутся — компактных основ достаточно для `gen_textures.py`.

`prepare.py` также делает `res/textures/skin0..3.png` (40×56) — органические
макро-текстуры, которыми `Monster.build()` модулирует процедурные спрайты тварей,
и `res/textures/beast0..3.png` (40×56) — фотографическую анатомию твари.
Из-за лимита в 10 ИИ-генераций за ход был снят один полноценный кадр
(`beast0.png`, изолированная на чёрном фоне тварь); из компактной копии
`base_beast.png` скрипт делает четыре цветовые морфы под четыре палитры ГПСЧ.
Промпт для `beast0.png`: «photorealistic full-body front-facing mutant creature,
isolated on pure black background, wet grey-green amphibian humanoid, veined
bulbous head, glowing pale eyes, long thin arms, hunched posture, harsh flashlight
horror photography, centered symmetrical full figure, no environment/text».

Промпты для повторной генерации исходников (положить сюда как
`concrete.png`, `tile.png`, `metal.png`, `cracked.png`, `slime.png`,
`flesh.png`, `violet.png`, `rustflesh.png`, `pale.png`, `beast0.png`, затем
запустить `python3 tools/photo_src/prepare.py`; без больших файлов скрипт
работает от уже лежащих `base_*.png` / `base_beast.png`):

* **concrete** — «weathered Soviet concrete panel wall, porous grey poured
  concrete, dark water stains, rust bleed, hairline cracks, flaking whitewash,
  flat straight-on, even lighting, no perspective/objects/text».
* **tile** — «old grimy off-white ceramic wall tiles, dark grout, chipped
  corners, soap scum, yellowed water stains, mold specks».
* **metal** — «corroded steel plate, orange rust flakes, pitted dark iron,
  rivet heads, scratches, peeling olive industrial paint, oily grime».
* **cracked** — «badly cracked reinforced concrete, deep jagged fissure with
  exposed rusty rebar, crumbling edges, damp black stains, efflorescence».
* **slime** — «wet translucent purple-grey organic biofilm over dark concrete,
  glossy mucus strands, puddles, violet iridescence, bubbles».
* **flesh** — «macro mottled amphibian mutant flesh, wet translucent grey-green
  skin, subcutaneous veins, pores, nodules, mucous sheen».
* **violet** — «macro diseased violet-black organic tissue, bioluminescent
  purple slime, glossy wet membrane, folds and veins».
* **rustflesh** — «macro corroded ochre-brown organic tissue with flaking rust,
  crusty ridges, dark oily crevices».
* **pale** — «macro pale cadaverous skin, bluish subcutaneous veins, pores,
  damp waxy sheen».

Все изображения — оригинальные генерации для этого проекта; спрайты и тексты
*Insmouse no Yakata* не копируются.

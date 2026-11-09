GigaKhrushchyovka - open graphics pack
=====================================
Edit any PNG here, then run: python3 tools/repack.py <this-folder>
No JDK, no compilation, no generators - just unzip / edit / repack.

textures/*.png in the JAR  - 16 wall/floor/ceiling textures (64x64)
audio/*.mid in the JAR     - music and sound effects
gfx/weapon.png             - first-person gun (64x54)
gfx/item-1..6.png          - pickup sprites (24x24)
gfx/title-0..2.png         - monsters on the title/ending screen
gfx/font.png               - 5x7 font atlas, 16 cols, 6x8 cell
gfx/crosshair*.png         - reticle alpha masks (21x21)
gfx/hud.png                - bottom HUD plate (240x56)
gfx/screen-*.png           - full-screen backgrounds (240x320)
gfx/overlay.png            - layer drawn on top of every frame
gfx/theme.txt              - all UI/fog colors as plain text
                            (KEY=0xAARRGGBB, or 0xRRGGBB)
gfx/source/*.png           - original photo sources for textures
gfx/parts/*.png            - MODULAR monsters: nine separate body parts
                            x 4 palettes, six 40x56 variants per 240x56 strip
                            (body, shadow, back, legs, armL, armR, torso,
                             head, front). Claws move independently; on death
                            every piece becomes a flying fragment.
gfx/screen-play.png        - optional: add it for a custom in-game
                            backdrop (same for screen-pause.png)

Alpha = 0 means transparent. Keep the pixel size of each file.
Delete a PNG to fall back to the built-in procedural sprite.
Full manifest: RESOURCES.md in the repository.

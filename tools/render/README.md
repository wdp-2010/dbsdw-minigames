# Model renderer

Draws one of the pack's item models offline, the way the 26.3 client does (element boxes, face UVs,
element rotations, team tint, display transforms), in headless Chrome with three.js. Use it to tune a
model; keep real-client screenshots (`tools/screenshot/`) for the final check.

```sh
cd tools/render && npm install --ignore-scripts      # once: three + playwright-core
node render.js --model dbsdw:item/gun --tint 2f7bff --views fp,gui,east,top --out /tmp/gun.png
```

Views: `fp` (first person, right hand: arm offset 0.56/-0.52/-0.72, hand FOV 70, 16:9), `gui`,
and the model-space sides `east`, `west`, `north`, `south`, `top`. It prints one JSON line: for `fp`
and `gui`, where the item sits on screen in percent (left/right/top/bottom/width/height), so many
checks need no image. Chrome comes from `/usr/bin/google-chrome` (override with `CHROME=`).

The first-person placement matched a real-client screenshot of the plan-6 gun to within about 1% of
the screen. The `gui` view is only approximate.

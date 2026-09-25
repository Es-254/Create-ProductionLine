// Generates the three Ponder structure schematics this mod ships:
//
//   src/main/resources/assets/create_productionline/ponder/{production_computer,scheme_loader,dismantler}.nbt
//
// Run with:  node tools/ponder-schematics.js <create-jar-or-any-ponder-schematic> <output-dir>
// The reference file is only used for its DataVersion and its base-plate checker parity; if you do not
// have one handy, any Create Ponder schematic works (Create ships 178 of them in assets/create/ponder).
//
// ---------------------------------------------------------------------------------------------
// TWO RULES THAT COST A ROUND EACH, SO THEY ARE WRITTEN DOWN HERE
//
// 1. `size` and every block's `pos` must be TAG_List of TAG_Int. Vanilla reads them with
//    CompoundTag#getListOrEmpty, so a TAG_Int_Array loads as an EMPTY list: the structure then has
//    size (0,0,0) and no blocks, with no error anywhere -- an empty scene that looks like a rendering
//    bug. (Its own writer uses newIntegerList, i.e. exactly the same list-of-int shape.)
//
// 2. Ponder's scene code can only touch positions inside the block bounding box that this schematic
//    actually places. `ReplaceBlocksInstruction` (what scene.world().setBlock/modifyBlock use) starts
//    with `if (level.getBounds().isInside(pos))`, and SchematicLevel#getBounds is computed from the
//    blocks that were PLACED -- not from `size`. A scene that places its machine at y=1 on a
//    plate-only schematic (y=0) therefore gets that machine silently dropped. So: everything a scene
//    shows belongs in here, and the box these blocks span is the box the scene may touch.
// ---------------------------------------------------------------------------------------------
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

// --- reference read (DataVersion + plate parity) -------------------------------------------
function readNbt(buf) {
  let p = 0;
  const u1 = () => buf[p++];
  const u2 = () => { const v = buf.readUInt16BE(p); p += 2; return v; };
  const i4 = () => { const v = buf.readInt32BE(p); p += 4; return v; };
  const str = () => { const n = u2(); const s = buf.toString('utf8', p, p + n); p += n; return s; };
  function payload(type) {
    switch (type) {
      case 1: return buf.readInt8(p++);
      case 2: return buf.readInt16BE((p += 2) - 2);
      case 3: return i4();
      case 4: { const v = Number(buf.readBigInt64BE(p)); p += 8; return v; }
      case 5: { const v = buf.readFloatBE(p); p += 4; return v; }
      case 6: { const v = buf.readDoubleBE(p); p += 8; return v; }
      case 7: { const n = i4(); const a = Buffer.from(buf.subarray(p, p + n)); p += n; return a; }
      case 8: return str();
      case 9: { const item = u1(); const n = i4(); const out = []; for (let i = 0; i < n; i++) out.push(payload(item)); return out; }
      case 10: { const out = {}; for (;;) { const t = u1(); if (t === 0) break; const name = str(); out[name] = payload(t); } return out; }
      case 11: { const n = i4(); const out = []; for (let i = 0; i < n; i++) out.push(i4()); return out; }
      case 12: { const n = i4(); const out = []; for (let i = 0; i < n; i++) { out.push(Number(buf.readBigInt64BE(p))); p += 8; } return out; }
      default: throw new Error('tag ' + type);
    }
  }
  u1(); str();
  return payload(10);
}

let refBuf = fs.readFileSync(process.argv[2]);
if (refBuf[0] === 0x1f && refBuf[1] === 0x8b) refBuf = zlib.gunzipSync(refBuf);
const refNbt = readNbt(refBuf);
const dataVersion = refNbt.DataVersion;
const plateBlock = (x, z) => {
  const b = refNbt.blocks.find((e) => e.pos[0] === x && e.pos[1] === 0 && e.pos[2] === z);
  return b ? refNbt.palette[b.state].Name : 'minecraft:white_concrete';
};
console.log(`reference DataVersion=${dataVersion}, plate parity (x,0)=${[0, 1, 2, 3, 4].map((x) => plateBlock(x, 0)).join(' ')}`);

// --- tiny NBT writer ------------------------------------------------------------------------
const tag = (id, name, payloadBuf) => {
  const nameBuf = Buffer.from(name, 'utf8');
  const len = Buffer.alloc(2); len.writeUInt16BE(nameBuf.length);
  return Buffer.concat([Buffer.from([id]), len, nameBuf, payloadBuf]);
};
const pInt = (v) => { const b = Buffer.alloc(4); b.writeInt32BE(v); return b; };
const pStr = (v) => { const s = Buffer.from(v, 'utf8'); const l = Buffer.alloc(2); l.writeUInt16BE(s.length); return Buffer.concat([l, s]); };
// rule 1 above: list of int, never an int array
const pIntList = (arr) => {
  const n = Buffer.alloc(4); n.writeInt32BE(arr.length);
  return Buffer.concat([Buffer.from([3]), n, ...arr.map(pInt)]);
};
const pList = (itemId, items) => {
  const n = Buffer.alloc(4); n.writeInt32BE(items.length);
  return Buffer.concat([Buffer.from([itemId]), n, ...items]);
};
const pCompound = (entries) => Buffer.concat([...entries, Buffer.from([0])]);

const SIZE = [5, 4, 5];

/** state -> palette entry NBT. Properties are the exact spellings Create's own schematics use. */
function paletteEntry(state) {
  const parts = Object.entries(state.Properties || {}).map(([k, v]) => tag(8, k, pStr(v)));
  const entries = [tag(8, 'Name', pStr(state.Name))];
  if (parts.length) entries.push(tag(10, 'Properties', pCompound(parts)));
  return pCompound(entries);
}

function buildSchematic(props) {
  // plate first so state 0/1 are the two plate blocks, then every prop
  const palette = [
    { Name: plateBlock(0, 0) },
    { Name: plateBlock(1, 0) },
  ];
  const blocks = [];
  for (let z = 0; z < SIZE[2]; z++) {
    for (let x = 0; x < SIZE[0]; x++) {
      blocks.push({ pos: [x, 0, z], state: (x + z) % 2 });
    }
  }
  for (const prop of props) {
    let idx = palette.findIndex((e) => e.Name === prop.Name && JSON.stringify(e.Properties || {}) === JSON.stringify(prop.Properties || {}));
    if (idx < 0) { palette.push({ Name: prop.Name, Properties: prop.Properties }); idx = palette.length - 1; }
    blocks.push({ pos: prop.pos, state: idx });
  }
  const blockTags = blocks.map((b) => pCompound([
    tag(9, 'pos', pIntList(b.pos)),
    tag(3, 'state', pInt(b.state)),
  ]));
  const root = pCompound([
    tag(3, 'DataVersion', pInt(dataVersion)),
    tag(9, 'size', pIntList(SIZE)),
    tag(9, 'palette', pList(10, palette.map(paletteEntry))),
    tag(9, 'blocks', pList(10, blockTags)),
    tag(9, 'entities', pList(0, [])),
  ]);
  return Buffer.concat([Buffer.from([10, 0, 0]), root]); // TAG_Compound, empty name
}

// --- the three scenes -----------------------------------------------------------------------
const MACHINE = (name, extra) => Object.assign({ Name: name, pos: [2, 1, 2] }, extra || {});
// A straight belt line, spelled the way Create's own schematics spell it (start -> middle -> end).
const belt = (x, z, part) => ({
  Name: 'create:belt', pos: [x, 1, z],
  Properties: { casing: 'false', part, facing: 'east', slope: 'horizontal' },
});
const deployer = (x, y, z) => ({
  Name: 'create:deployer', pos: [x, y, z],
  Properties: { facing: 'down', axis_along_first: 'false' },
});

/**
 * Redstone dust between the cabinet and the lamp: the two connection flags are what make the wire draw
 * as a straight run instead of a dot, and `power` is what the scene raises when the narration says the
 * cabinet emits a signal.
 */
const dust = (x, z) => ({
  Name: 'minecraft:redstone_wire', pos: [x, 1, z],
  Properties: { north: 'false', east: 'true', south: 'false', west: 'true', power: '0' },
});

/**
 * The motor that drives the closing picture's belt, one cell north of the belt's first pulley. A belt
 * travelling along x is turned by pulleys whose axis runs along z, so the motor's output has to point
 * at the belt from the side: at (0,1,0) facing south it drives (0,1,1). Ponder scenes do run kinetics
 * (the scene calls setKineticSpeed on exactly these cells), and the author asked for the line to be
 * shown connected to a power source rather than as a static picture.
 */
const MOTOR_Z = 0;
const motor = () => ({ Name: 'create:creative_motor', pos: [0, 1, MOTOR_Z], Properties: { facing: 'south' } });

/**
 * The Deployers sit above the belt's first cell and above its last cell, and hang **two** cells above it:
 * a Deployer always acts on the position two blocks in front of itself (Create's own scene says so in as
 * many words), so a Deployer at y=2 facing down would reach the plate at y=0 and miss the belt at y=1
 * entirely. One empty cell between the hand and the belt is what the author called "空一格", and it is
 * how a real sequenced assembly is built. The last cell is deliberate too: an item that reaches the end
 * of a belt stops there by itself, so the picture never depends on how fast the belt happens to run.
 */
const DEPLOYER_Y = 3;

const schematics = {
  // Chapter 1 — the machine alone on the plate.
  production_computer: [MACHINE('create_productionline:production_computer')],

  // Chapter 2 — the cabinet, the redstone line it drives, and the powered belt line the plan describes,
  // one row in front of the machine (z=1, so the preview never shares a position with the cabinet at z=2).
  scheme_loader: [
    MACHINE('create_productionline:scheme_loader', { Properties: { fill: '0' } }),
    // The signal path the narration talks about: cabinet (2,1,2) -> dust (3,1,2) -> lamp (4,1,2). The
    // lamp starts unlit and the scene powers dust and lamp together, so the picture shows the signal
    // arriving rather than a lamp that was lit all along.
    dust(3, 2),
    { Name: 'minecraft:redstone_lamp', pos: [4, 1, 2], Properties: { lit: 'false' } },
    motor(),
    belt(0, 1, 'start'), belt(1, 1, 'middle'), belt(2, 1, 'middle'), belt(3, 1, 'middle'), belt(4, 1, 'end'),
    deployer(0, DEPLOYER_Y, 1), deployer(4, DEPLOYER_Y, 1),
  ],

  // Its own entry — the machine alone, like chapter 1.
  dismantler: [MACHINE('create_productionline:dismantler')],
};

const outDir = process.argv[3];
fs.mkdirSync(outDir, { recursive: true });
for (const [name, props] of Object.entries(schematics)) {
  const file = buildSchematic(props);
  const target = path.join(outDir, `${name}.nbt`);
  fs.writeFileSync(target, zlib.gzipSync(file, { level: 9 }));
  console.log(`wrote ${target} (${fs.statSync(target).size} B, ${props.length + 25} blocks)`);
}

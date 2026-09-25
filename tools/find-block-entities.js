#!/usr/bin/env node
/*
 * Lists the block entities of one block id in a Minecraft world folder.
 *
 * Reads the region files directly (read-only) and prints every block entity whose
 * id matches, with its coordinates — the quickest way to find where a machine is
 * actually standing when a world has been moved around.
 *
 * Region format: a 4096-byte location table (offset in 4 KiB sectors, 3 bytes, plus
 * a 1-byte sector count), then 4096 bytes of timestamps, then the chunk payloads,
 * each prefixed with a 4-byte big-endian length and a 1-byte compression id
 * (1 = gzip, 2 = zlib, 3 = none).
 *
 * Usage:
 *   node tools/find-block-entities.js <world folder> <block id> [dimension]
 */

'use strict';

const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const TAG = {
  0: 'end', 1: 'byte', 2: 'short', 3: 'int', 4: 'long', 5: 'float', 6: 'double',
  7: 'byteArray', 8: 'string', 9: 'list', 10: 'compound', 11: 'intArray', 12: 'longArray',
};

function readNbt(buffer) {
  let offset = 0;
  const u1 = () => buffer[offset++];
  const i1 = () => buffer.readInt8(offset++);
  const i2 = () => { const v = buffer.readInt16BE(offset); offset += 2; return v; };
  const i4 = () => { const v = buffer.readInt32BE(offset); offset += 4; return v; };
  const i8 = () => { const v = buffer.readBigInt64BE(offset); offset += 8; return Number(v); };
  const f4 = () => { const v = buffer.readFloatBE(offset); offset += 4; return v; };
  const f8 = () => { const v = buffer.readDoubleBE(offset); offset += 8; return v; };
  const str = () => { const n = buffer.readUInt16BE(offset); offset += 2; const s = buffer.toString('utf8', offset, offset + n); offset += n; return s; };

  function payload(type) {
    switch (TAG[type]) {
      case 'byte': return i1();
      case 'short': return i2();
      case 'int': return i4();
      case 'long': return i8();
      case 'float': return f4();
      case 'double': return f8();
      case 'byteArray': { const n = i4(); offset += n; return `<${n} bytes>`; }
      case 'string': return str();
      case 'list': {
        const itemType = u1();
        const n = i4();
        const out = [];
        for (let i = 0; i < n; i++) out.push(payload(itemType));
        return out;
      }
      case 'compound': {
        const out = {};
        for (;;) {
          const t = u1();
          if (t === 0) return out;
          out[str()] = payload(t);
        }
      }
      case 'intArray': { const n = i4(); const out = []; for (let i = 0; i < n; i++) out.push(i4()); return out; }
      case 'longArray': { const n = i4(); const out = []; for (let i = 0; i < n; i++) out.push(i8()); return out; }
      default: throw new Error(`unsupported tag ${type}`);
    }
  }

  const type = u1();
  if (type !== 10) throw new Error('root is not a compound');
  str();
  return payload(type);
}

function inflate(compression, payload) {
  if (compression === 1) return zlib.gunzipSync(payload);
  if (compression === 2) return zlib.inflateSync(payload);
  if (compression === 3) return payload;
  throw new Error(`unknown compression ${compression}`);
}

function main() {
  const world = process.argv[2];
  const wanted = process.argv[3];
  if (!world || !wanted) {
    console.error('usage: node find-block-entities.js <world folder> <block id> [dimension]');
    process.exit(1);
  }
  const dimension = process.argv[4] || 'overworld';
  const regionDir = dimension === 'overworld'
    ? path.join(world, 'region')
    : path.join(world, `DIM${dimension}`);

  const found = [];
  const files = fs.existsSync(regionDir) ? fs.readdirSync(regionDir).filter((f) => f.endsWith('.mca')) : [];
  for (const file of files) {
    const match = /^r\.(-?\d+)\.(-?\d+)\.mca$/.exec(file);
    if (!match) continue;
    const baseX = Number(match[1]) * 32;
    const baseZ = Number(match[2]) * 32;
    const buffer = fs.readFileSync(path.join(regionDir, file));
    for (let index = 0; index < 1024; index++) {
      const entry = index * 4;
      const sectorOffset = (buffer[entry] << 16) | (buffer[entry + 1] << 8) | buffer[entry + 2];
      const sectorCount = buffer[entry + 3];
      if (sectorOffset === 0 || sectorCount === 0) continue;
      const start = sectorOffset * 4096;
      if (start + 5 > buffer.length) continue;
      const length = buffer.readUInt32BE(start);
      const compression = buffer[start + 4];
      if (length === 0 || start + 4 + length > buffer.length) continue;
      let chunk;
      try {
        chunk = readNbt(inflate(compression, buffer.subarray(start + 5, start + 4 + length)));
      } catch (e) {
        continue; // a chunk we cannot read is not worth failing the whole scan for
      }
      for (const entity of chunk.block_entities || []) {
        if (entity.id !== wanted) continue;
        found.push({
          dimension,
          x: entity.x,
          y: entity.y,
          z: entity.z,
          chunk: [baseX + (index % 32), baseZ + Math.floor(index / 32)],
          extra: Object.keys(entity).filter((k) => k !== 'id' && k !== 'x' && k !== 'y' && k !== 'z').join(','),
        });
      }
    }
  }

  if (found.length === 0) {
    console.log(`no ${wanted} block entity found in ${regionDir}`);
  } else {
    for (const entry of found) {
      console.log(`${entry.dimension}  ${entry.x} ${entry.y} ${entry.z}   (chunk ${entry.chunk[0]},${entry.chunk[1]})   nbt: ${entry.extra}`);
    }
    console.log(`${found.length} found`);
  }
}

main();

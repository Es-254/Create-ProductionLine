#!/usr/bin/env node
/*
 * Minimal NBT reader, just enough to print where a player was standing.
 *
 * Usage: node nbt-peek.js <file.dat> [key ...]
 * Prints the requested top-level paths (e.g. Pos Rotation Dimension) as JSON.
 */

'use strict';

const fs = require('fs');
const zlib = require('zlib');

const TAG = {
  0: 'end', 1: 'byte', 2: 'short', 3: 'int', 4: 'long', 5: 'float', 6: 'double',
  7: 'byteArray', 8: 'string', 9: 'list', 10: 'compound', 11: 'intArray', 12: 'longArray',
};

function reader(buffer) {
  let offset = 0;
  return {
    u1: () => buffer[offset++],
    i1: () => buffer.readInt8(offset++),
    i2: () => { const v = buffer.readInt16BE(offset); offset += 2; return v; },
    u2: () => { const v = buffer.readUInt16BE(offset); offset += 2; return v; },
    i4: () => { const v = buffer.readInt32BE(offset); offset += 4; return v; },
    i8: () => { const v = buffer.readBigInt64BE(offset); offset += 8; return v; },
    f4: () => { const v = buffer.readFloatBE(offset); offset += 4; return v; },
    f8: () => { const v = buffer.readDoubleBE(offset); offset += 8; return v; },
    str: () => { const length = buffer.readUInt16BE(offset); offset += 2; const s = buffer.toString('utf8', offset, offset + length); offset += length; return s; },
    skip: (n) => { offset += n; },
    get offset() { return offset; },
    set offset(value) { offset = value; },
  };
}

function readPayload(r, type) {
  switch (TAG[type]) {
    case 'byte': return r.i1();
    case 'short': return r.i2();
    case 'int': return r.i4();
    case 'long': return Number(r.i8());
    case 'float': return r.f4();
    case 'double': return r.f8();
    case 'byteArray': { const n = r.i4(); const out = []; for (let i = 0; i < n; i++) out.push(r.i1()); return out; }
    case 'string': return r.str();
    case 'list': {
      const itemType = r.u1();
      const n = r.i4();
      const out = [];
      for (let i = 0; i < n; i++) out.push(readPayload(r, itemType));
      return out;
    }
    case 'compound': {
      const out = {};
      for (;;) {
        const t = r.u1();
        if (t === 0) return out;
        out[r.str()] = readPayload(r, t);
      }
    }
    case 'intArray': { const n = r.i4(); const out = []; for (let i = 0; i < n; i++) out.push(r.i4()); return out; }
    case 'longArray': { const n = r.i4(); const out = []; for (let i = 0; i < n; i++) out.push(Number(r.i8())); return out; }
    default: throw new Error(`unsupported tag ${type} (${TAG[type]})`);
  }
}

function readNbt(buffer) {
  const r = reader(buffer);
  const type = r.u1();
  if (type !== 10) throw new Error(`root is ${TAG[type]}, expected compound`);
  r.str();
  return readPayload(r, type);
}

const file = process.argv[2];
const keys = process.argv.slice(3);
const raw = fs.readFileSync(file);
let data;
try {
  data = zlib.gunzipSync(raw);
} catch (e) {
  data = raw; // already uncompressed
}
const root = readNbt(data);
const wanted = keys.length > 0 ? keys : Object.keys(root);
const out = {};
for (const key of wanted) {
  out[key] = root[key];
}
console.log(JSON.stringify(out, null, 2));

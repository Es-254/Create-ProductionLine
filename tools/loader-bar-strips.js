#!/usr/bin/env node
/*
 * Splits the Scheme Loader's bar models into one model per "strip".
 *
 * Why: the loader's front bar used to be baked into the block model. The
 * blockstate file mapped its `fill` property (0..16) onto six models
 * (scheme_loader_empty / scheme_loader_bar_1..5 / scheme_loader), so every
 * change re-meshed the chunk section and shipped a block-update packet, and the
 * bar could only ever show six discrete pictures.
 *
 * The bar is now drawn by a renderer (Flywheel visual + vanilla BER fallback),
 * which needs one model per strip instead of one model per stage:
 *
 *   scheme_loader_strip_1 .. scheme_loader_strip_6
 *
 * Each strip model is derived from the difference between two stage models:
 * strip N = elements(bar model for stage N) - elements(bar model for stage N-1).
 * Nothing is hand-drawn: the source models stay the single source of truth, and
 * the script fails loudly if the stages are not cumulative (a strip would then
 * have to come from somewhere else).
 *
 * Usage:
 *   node tools/loader-bar-strips.js            # report only
 *   node tools/loader-bar-strips.js --write    # report + write strip models
 */

'use strict';

const fs = require('fs');
const path = require('path');

const ASSETS = path.join(
  __dirname, '..',
  'src', 'main', 'resources', 'assets', 'create_productionline',
);
const MODEL_DIR = path.join(ASSETS, 'models', 'block');

/** Stage models, in bar order: the model each `fill` stage used to select. */
const STAGES = [
  { stage: 1, file: 'scheme_loader_bar_1.json' },
  { stage: 2, file: 'scheme_loader_bar_2.json' },
  { stage: 3, file: 'scheme_loader_bar_3.json' },
  { stage: 4, file: 'scheme_loader_bar_4.json' },
  { stage: 5, file: 'scheme_loader_bar_5.json' },
  { stage: 6, file: 'scheme_loader.json' },
];
const BASELINE = 'scheme_loader_empty.json';

function readJson(file) {
  return JSON.parse(fs.readFileSync(path.join(MODEL_DIR, file), 'utf8'));
}

/** Recursively key-sorted clone, so two copies of the same cuboid compare equal. */
function canonical(value) {
  if (Array.isArray(value)) {
    return value.map(canonical);
  }
  if (value && typeof value === 'object') {
    const sorted = {};
    for (const key of Object.keys(value).sort()) {
      sorted[key] = canonical(value[key]);
    }
    return sorted;
  }
  return value;
}

/** Canonical form of an element, so two copies of the same cuboid match. */
function signature(element) {
  return JSON.stringify(canonical(element));
}

function elementsOf(model) {
  if (!Array.isArray(model.elements)) {
    throw new Error('model has no elements array');
  }
  return model.elements;
}

function missing(from, inSet) {
  return from.filter((element) => !inSet.has(signature(element)));
}

function main() {
  const write = process.argv.includes('--write');
  const baseline = readJson(BASELINE);
  const baselineSignatures = new Set(elementsOf(baseline).map(signature));

  console.log(`${BASELINE}: ${elementsOf(baseline).length} elements, keys=[${Object.keys(baseline).join(',')}]`);

  const extras = [];
  for (const { stage, file } of STAGES) {
    const model = readJson(file);
    const extra = missing(elementsOf(model), baselineSignatures);
    // Every stage model must keep the baseline geometry untouched.
    const lost = missing(elementsOf(baseline), new Set(elementsOf(model).map(signature)));
    if (lost.length > 0) {
      throw new Error(`${file} does not contain ${lost.length} of the baseline elements`);
    }
    extras.push({ stage, file, model, extra });
    console.log(
      `${file.padEnd(30)} elements=${String(elementsOf(model).length).padStart(3)}` +
      ` bar=${String(extra.length).padStart(3)} keys=[${Object.keys(model).join(',')}]`,
    );
  }

  if (extras[extras.length - 1].extra.length === 0) {
    throw new Error('the full stage carries no bar elements — stages are probably not cumulative');
  }

  // strip N = stage N minus stage N-1 (nothing to do for N = 1).
  const strips = [];
  for (let i = 0; i < extras.length; i++) {
    const previous = i === 0 ? [] : extras[i - 1].extra;
    const previousSignatures = new Set(previous.map(signature));
    const strip = missing(extras[i].extra, previousSignatures);
    const gone = missing(previous, new Set(extras[i].extra.map(signature)));
    if (gone.length > 0) {
      throw new Error(`stage ${extras[i].stage} dropped ${gone.length} bar elements of stage ${extras[i].stage - 1}`);
    }
    if (strip.length === 0) {
      throw new Error(`stage ${extras[i].stage} adds no bar elements over stage ${extras[i].stage - 1}`);
    }
    strips.push({ stage: extras[i].stage, source: extras[i].file, elements: strip });
  }

  const total = strips.reduce((sum, strip) => sum + strip.elements.length, 0);
  if (total !== extras[extras.length - 1].extra.length) {
    throw new Error(`strips hold ${total} elements but the full bar has ${extras[extras.length - 1].extra.length}`);
  }

  console.log('');
  for (const strip of strips) {
    // Cube bounds make the stage order readable in the log: stage 1 is the top
    // strip, later stages stack downwards.
    const bounds = strip.elements
      .map((element) => `[${element.from.join(',')}]->[${element.to.join(',')}]`)
      .join(' ');
    console.log(`strip ${strip.stage}: ${String(strip.elements.length).padStart(3)} elements ${bounds} (from ${strip.source})`);
  }
  console.log(`total bar elements: ${total} — matches the full stage`);

  if (!write) {
    console.log('\nreport only; pass --write to emit the strip models');
    return;
  }

  for (const strip of strips) {
    const source = readJson(strip.source);
    // The stage models all inherit minecraft:block/block — vanilla display
    // transforms and ambient occlusion defaults, no geometry — so the strips
    // keep the same parent and stay consistent with the block model.
    const model = {
      parent: source.parent,
      textures: source.textures,
      elements: strip.elements,
    };
    if (source.ambientocclusion !== undefined) {
      model.ambientocclusion = source.ambientocclusion;
    }
    const file = path.join(MODEL_DIR, `scheme_loader_strip_${strip.stage}.json`);
    fs.writeFileSync(file, `${JSON.stringify(model, null, 2)}\n`, 'utf8');
    console.log(`wrote ${path.relative(process.cwd(), file)} (${fs.statSync(file).size} B)`);
  }
}

main();

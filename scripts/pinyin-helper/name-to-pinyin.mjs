import fs from 'node:fs';
import { pinyin } from 'pinyin-pro';

// 拼音辅助脚本
const [inputPath, outputPath] = process.argv.slice(2);

if (!inputPath || !outputPath) {
  console.error('Usage: node name-to-pinyin.mjs <input.json> <output.json>');
  process.exit(1);
}

const names = JSON.parse(fs.readFileSync(inputPath, 'utf8'));

const result = names.map((name) => {
  const raw = pinyin(String(name ?? ''), {
    toneType: 'none',
    type: 'array',
  });

  const merged = (Array.isArray(raw) ? raw.join('') : String(raw))
    .toLowerCase()
    .normalize('NFKD')
    .replace(/[^a-z0-9]/g, '');

  return merged;
});

fs.writeFileSync(outputPath, JSON.stringify(result, null, 2), 'utf8');

const d = require('C:/Users/julye/IdeaProjects/vbhelper/app/src/main/assets/blast_evolution.json');
const rx = /data\b|digimental|pendulum|bracelet|d-ark|d-scanner|d-spirit|^(vaccine|virus|data|free)$|card game|digi-egg|egg\b|spirit of|digivice|digicore|digisoul/i;
const pairs = [];
d.jogress.forEach((p) => {
  [p.a, p.b].forEach((x) => {
    if (rx.test(x)) pairs.push(p.a + ' + ' + p.b + ' => ' + p.result);
  });
});
console.log('suspect:', pairs.length);
pairs.slice(0, 30).forEach((s) => console.log(' ' + s));

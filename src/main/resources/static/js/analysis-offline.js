// Offline CSV analysis: parse -> indicators -> signals -> render

export function parseCsv(text) {
  const lines = text.trim().split(/\r?\n/);
  const header = lines[0].split(',').map(s => s.trim().toLowerCase());
  const idx = (k) => header.indexOf(k);
  const ti = idx('time');
  const oi = idx('open');
  const hi = idx('high');
  const li = idx('low');
  const ci = idx('close');
  const vi = idx('volume');
  const rows = [];
  for (let i = 1; i < lines.length; i++) {
    const cols = lines[i].split(',');
    if (cols.length < header.length) continue;
    rows.push({
      time: isNaN(+cols[ti]) ? new Date(cols[ti]).getTime() : +cols[ti],
      open: +cols[oi], high: +cols[hi], low: +cols[li], close: +cols[ci], volume: +cols[vi]
    });
  }
  rows.sort((a,b)=>a.time-b.time);
  return rows;
}

export function sma(arr, period) {
  const out = new Array(arr.length).fill(null);
  let sum = 0;
  for (let i = 0; i < arr.length; i++) {
    sum += arr[i];
    if (i >= period) sum -= arr[i - period];
    if (i >= period - 1) out[i] = sum / period;
  }
  return out;
}

export function ema(arr, period) {
  const out = new Array(arr.length).fill(null);
  const k = 2 / (period + 1);
  let prev = null;
  for (let i = 0; i < arr.length; i++) {
    const v = arr[i];
    prev = prev == null ? v : v * k + prev * (1 - k);
    if (i >= period - 1) out[i] = prev;
  }
  return out;
}

export function macd(arr, fast=12, slow=26, signal=9) {
  const emaFast = ema(arr, fast);
  const emaSlow = ema(arr, slow);
  const dif = arr.map((_,i)=> (emaFast[i]!=null && emaSlow[i]!=null) ? (emaFast[i]-emaSlow[i]) : null);
  const dea = ema(dif.map(v=> v==null?0:v), signal).map((v,i)=> dif[i]==null?null:v);
  const hist = dif.map((v,i)=> (v==null||dea[i]==null)?null:(v-dea[i]));
  return { dif, dea, hist };
}

export function rsi(arr, period=14) {
  const out = new Array(arr.length).fill(null);
  let gain = 0, loss = 0;
  for (let i = 1; i < arr.length; i++) {
    const ch = arr[i] - arr[i-1];
    const g = Math.max(0, ch), l = Math.max(0, -ch);
    if (i <= period) { gain += g; loss += l; if (i === period) out[i] = 100 - 100/(1 + (gain/period)/(loss/period||1e-9)); }
    else {
      gain = (gain*(period-1) + g) / period;
      loss = (loss*(period-1) + l) / period;
      out[i] = 100 - 100/(1 + (gain/(loss||1e-9)));
    }
  }
  return out;
}

export function boll(arr, period=20, k=2) {
  const mid = sma(arr, period);
  const up = new Array(arr.length).fill(null);
  const dn = new Array(arr.length).fill(null);
  for (let i = 0; i < arr.length; i++) {
    if (i >= period - 1) {
      let mean = mid[i];
      let s2 = 0;
      for (let j = i - period + 1; j <= i; j++) s2 += Math.pow(arr[j]-mean, 2);
      const sd = Math.sqrt(s2 / period);
      up[i] = mean + k*sd; dn[i] = mean - k*sd;
    }
  }
  return { mid, up, dn };
}

export function signals(closes, smaFast, smaSlow, rsi14) {
  const sigs = [];
  for (let i = 1; i < closes.length; i++) {
    if (smaFast[i-1]!=null && smaSlow[i-1]!=null && smaFast[i]!=null && smaSlow[i]!=null) {
      const prev = smaFast[i-1] - smaSlow[i-1];
      const cur = smaFast[i] - smaSlow[i];
      if (prev <= 0 && cur > 0) sigs.push({ i, type:'BUY', reason:'SMA crossover' });
      if (prev >= 0 && cur < 0) sigs.push({ i, type:'SELL', reason:'SMA crossover' });
    }
    if (rsi14[i]!=null) {
      if (rsi14[i] < 30) sigs.push({ i, type:'BUY', reason:'RSI oversold' });
      if (rsi14[i] > 70) sigs.push({ i, type:'SELL', reason:'RSI overbought' });
    }
  }
  return sigs;
}

export function basicPerformance(rows, sigs) {
  // naive: buy on BUY, sell on SELL next close; no costs
  let pos = 0, entry = 0, pnl = 0;
  for (const s of sigs) {
    const px = rows[s.i].close;
    if (s.type==='BUY' && pos===0) { pos=1; entry=px; }
    else if (s.type==='SELL' && pos===1) { pnl += (px-entry)/entry; pos=0; }
  }
  return { trades: pnl, retPct: pnl*100 };
}

export function runOfflineAnalysis({ text, chart, overlays = { smaFast:5, smaSlow:20, rsi:14, boll: {p:20,k:2} } }) {
  const rows = parseCsv(text);
  const times = rows.map(r=> r.time);
  const o = rows.map(r=> r.open), h = rows.map(r=> r.high), l = rows.map(r=> r.low), c = rows.map(r=> r.close), v = rows.map(r=> r.volume);
  const s5 = sma(c, overlays.smaFast);
  const s20 = sma(c, overlays.smaSlow);
  const r14 = rsi(c, overlays.rsi);
  const bb = boll(c, overlays.boll.p, overlays.boll.k);
  const mc = macd(c);
  const sigs = signals(c, s5, s20, r14);
  const perf = basicPerformance(rows, sigs);

  const option = {
    animation: false,
    tooltip: { trigger: 'axis' },
    axisPointer: { link: [{ xAxisIndex: 'all' }] },
    grid: [{ left: 60, right: 30, height: '60%' }, { left: 60, right: 30, top: '70%', height: '20%' }],
    xAxis: [
      { type: 'time', data: times },
      { type: 'time', gridIndex: 1, data: times }
    ],
    yAxis: [ { scale: true }, { gridIndex: 1, scale: true } ],
    series: [
      { type: 'candlestick', data: rows.map(r=> [r.open, r.close, r.low, r.high]) },
      { type: 'line', data: s5, smooth: true, showSymbol: false, lineStyle:{width:1}, name:`SMA(${overlays.smaFast})` },
      { type: 'line', data: s20, smooth: true, showSymbol: false, lineStyle:{width:1}, name:`SMA(${overlays.smaSlow})` },
      { type: 'line', data: bb.up, smooth: true, showSymbol: false, lineStyle:{width:1, type:'dashed'}, name:'BOLL UP' },
      { type: 'line', data: bb.mid, smooth: true, showSymbol: false, lineStyle:{width:1, type:'dotted'}, name:'BOLL MID' },
      { type: 'line', data: bb.dn, smooth: true, showSymbol: false, lineStyle:{width:1, type:'dashed'}, name:'BOLL DN' },
      { type: 'bar', xAxisIndex:1, yAxisIndex:1, data: v, name:'VOL', itemStyle:{color:'#888'} }
    ]
  };
  chart.setOption(option, true);

  return { rows, signals: sigs, perf, indicators: { s5, s20, r14, bb, macd: mc } };
}



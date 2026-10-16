/* Read-only presentation of existing reports. No banking operations or report filters are changed. */
(async function () {
  const el = id => document.getElementById(id);
  const escape = value => String(value).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  let totals = [], history = [];
  function scaled(value) {
    const text = String(value); const negative = text.startsWith('-');
    const [whole, fraction = ''] = text.replace(/^-/, '').split('.');
    const result = BigInt(whole + fraction.padEnd(4, '0').slice(0, 4));
    return negative ? -result : result;
  }
  function chart(currency) {
    const rows = history.filter(row => row.currency === currency);
    el('overview-chart-key').textContent = currency + ' · posted balance';
    if (!rows.length) { el('overview-chart').innerHTML = '<p class="chart-empty">No posted balance history for this currency.</p>'; return; }
    const values = rows.map(row => scaled(row.closingBalance));
    const min = values.reduce((a,b) => a < b ? a : b, 0n), max = values.reduce((a,b) => a > b ? a : b, 0n);
    const span = max - min || 1n;
    const left = 100, right = 740, top = 22, bottom = 185;
    // Convert only bounded ratios to Number for SVG geometry; monetary values remain exact strings.
    const x = i => left + (right-left) * i / Math.max(rows.length-1, 1);
    const y = value => bottom - Number((value-min)*100000n/span)/100000*(bottom-top);
    const points = values.map((v,i) => [x(i), y(v)]);
    const path = points.map(([px,py],i) => `${i?'L':'M'} ${px.toFixed(2)} ${py.toFixed(2)}`).join(' ');
    let grid = '';
    for (let i=0;i<3;i++) {
      const at = i===0 ? max : i===2 ? min : min+(max-min)/2n;
      const label = i===0 ? rows[values.indexOf(max)]?.closingBalance || '0' : i===2 ? (min===0n?'0':rows[values.indexOf(min)].closingBalance) : null;
      // Midpoint labels use a scaled integer representation, never floating-point money arithmetic.
      const abs = at < 0n ? -at : at; const raw = `${at<0n?'-':''}${abs/10000n}.${(abs%10000n).toString().padStart(4,'0')}`;
      grid += `<line class="chart-grid" x1="${left}" x2="${right}" y1="${top+(bottom-top)*i/2}" y2="${top+(bottom-top)*i/2}"/><text class="chart-axis" x="86" y="${top+(bottom-top)*i/2+4}" text-anchor="end">${escape(Money.compact(label || raw,currency) || Money.exact(label || raw,currency,false))}</text>`;
    }
    const labels = rows.map((row,i) => {
      if(i!==0 && i!==rows.length-1 && i%2!==0)return '';
      const month = new Date(row.month+'-01T00:00:00Z').toLocaleDateString('en',{month:'short',timeZone:'UTC'});
      return `<text class="chart-axis" x="${x(i)}" y="215" text-anchor="middle">${escape(month)}</text>`;
    }).join('');
    const last = points.at(-1);
    const accessible = `${currency} monthly closing deposit balances. ` + rows.map(r => `${r.month}: ${Money.exact(r.closingBalance,currency)}`).join('; ');
    el('overview-chart').innerHTML = `<svg viewBox="0 0 760 230" role="img" aria-label="${escape(accessible)}"><defs><linearGradient id="balance-fill" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stop-color="#5374ff" stop-opacity=".3"/><stop offset="100%" stop-color="#5374ff" stop-opacity="0"/></linearGradient></defs>${grid}<path class="chart-area" d="${path} L ${last[0]} ${bottom} L ${points[0][0]} ${bottom} Z"/><path class="chart-line" d="${path}"/><circle cx="${last[0]}" cy="${last[1]}" r="5" fill="var(--accent)" stroke="var(--panel)" stroke-width="3"/>${labels}</svg>`;
  }
  function selectCurrency() {
    const currency = el('overview-currency').value, total = totals.find(t => t.currency === currency);
    if (!total) return;
    el('overview-liability').textContent = Money.exact(total.totalLiability,currency);
    el('overview-compact').textContent = Money.compact(total.totalLiability,currency) || 'Deposit balances + accrued interest';
    el('overview-accrued').textContent = 'Accrued ' + Money.exact(total.accruedInterest,currency);
    chart(currency);
  }
  try {
    const [portfolio, liability, growth] = await Promise.all([
      apiRequest('GET','/reports/fd-accounts?reportType=PORTFOLIO_SUMMARY'),
      apiRequest('GET','/reports/fd-accounts?reportType=TOTAL_LIABILITY'),
      apiRequest('GET','/reports/fd-accounts?reportType=MONTHLY_GROWTH')
    ]);
    totals=liability.totals;history=growth.rows;
    const counts={ACTIVE:0,MATURED_CLOSED:0,MATURED_RENEWED:0,CLOSED:0};
    portfolio.rows.forEach(row=>{if(row.status in counts) counts[row.status]++;});
    const count=portfolio.rows.length, percentage=count?Math.round(counts.ACTIVE/count*100):0;
    el('overview-total').textContent=`${count} account${count===1?'':'s'}`;
    el('overview-share').textContent=percentage+'%';
    el('overview-gauge').style.setProperty('--progress',String(percentage));
    el('overview-active').textContent=`${counts.ACTIVE} active deposit${counts.ACTIVE===1?'':'s'}`;
    for(const [id,status] of [['status-active','ACTIVE'],['status-matured','MATURED_CLOSED'],['status-renewed','MATURED_RENEWED'],['status-closed','CLOSED']])el(id).textContent=String(counts[status]);
    const picker=el('overview-currency');picker.replaceChildren(...totals.map(t=>new Option(t.currency,t.currency)));picker.disabled=!totals.length;
    picker.addEventListener('change',selectCurrency);
    if(totals.length) selectCurrency();
    else {el('overview-liability').textContent='No active deposits';el('overview-compact').textContent='Your portfolio overview will appear here.';el('overview-chart').innerHTML='<p class="chart-empty">No active currency selected. Generate a monthly growth report below to review historical balances.</p>';el('overview-accrued').textContent='No accrued interest';}
    el('overview-status').textContent=`Current position · Business date ${portfolio.businessDate} · Totals stay separate by currency`;
  } catch(error) {
    el('overview-status').textContent='Overview unavailable. You can still use the report controls below. '+error.message;
    el('overview-compact').textContent='Overview could not be loaded';
    el('overview-active').textContent='Account status unavailable';
    el('overview-chart').innerHTML='<p class="chart-empty">Portfolio history is currently unavailable.</p>';
  }
})();

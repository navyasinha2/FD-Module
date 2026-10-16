/* A shared visual shell. Existing forms, IDs, links, and their event handlers are retained. */
(function () {
  const main = document.querySelector('main.page');
  const navigation = main.querySelector('.topnav');
  const icons = {
    overview:'<rect x="3" y="3" width="7" height="7" rx="2"/><rect x="14" y="3" width="7" height="7" rx="2"/><rect x="3" y="14" width="7" height="7" rx="2"/><rect x="14" y="14" width="7" height="7" rx="2"/>',
    account:'<rect x="3" y="5" width="18" height="15" rx="3"/><path d="M3 10h18M7 15h4M16 2v6"/>',
    withdraw:'<path d="M4 7h15m-4-4 4 4-4 4M20 17H5m4-4-4 4 4 4"/>',
    operations:'<path d="M4 7h16M4 17h16M8 4v6m8 4v6"/><circle cx="8" cy="7" r="2"/><circle cx="16" cy="17" r="2"/>',
    moon:'<path d="M20.9 13A9 9 0 0 1 11 3.1 9 9 0 1 0 20.9 13Z"/>',
    sun:'<circle cx="12" cy="12" r="4"/><path d="M12 2v2m0 16v2M2 12h2m16 0h2M5 5l1.5 1.5m11 11L19 19M5 19l1.5-1.5m11-11L19 5"/>',
    plus:'<path d="M12 5v14M5 12h14"/>', menu:'<path d="M4 6h16M4 12h16M4 18h16"/>',
    close:'<path d="m6 6 12 12M6 18 18 6"/>', arrow:'<path d="M7 17 17 7M7 7h10v10"/>'
  };
  const icon = name => `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${icons[name]}</svg>`;
  const linkTo = file => { const url = new URL(file, location.href); const api = new URLSearchParams(location.search).get('apiBase'); if (api) url.searchParams.set('apiBase', api); return url.href; };
  const file = location.pathname.split('/').pop() || 'index.html';
  const titles = { 'index.html':'Open deposit', 'reports.html':'Reports & insights', 'operations.html':'Operations', 'withdraw.html':'Withdraw deposit' };
  const aside = document.createElement('aside'); aside.className = 'app-sidebar'; aside.id = 'app-sidebar';
  aside.innerHTML = `<a class="brand" href="${linkTo('reports.html')}" aria-label="BankLab reports"><span class="brand-mark"><svg viewBox="0 0 32 32" fill="none" aria-hidden="true"><ellipse cx="16" cy="16" rx="6" ry="13" stroke="currentColor" stroke-width="1.6" transform="rotate(45 16 16)"/><ellipse cx="16" cy="16" rx="6" ry="13" stroke="currentColor" stroke-width="1.6" transform="rotate(-45 16 16)"/><circle cx="16" cy="16" r="2" fill="currentColor"/></svg></span>BankLab<span class="brand-dot">.</span></a><button class="sidebar-close icon-button" type="button" aria-label="Close navigation">${icon('close')}</button><div class="sidebar-intro"><span class="eyebrow">YOUR BANKING WORKSPACE</span><h2>A little more clarity.<br>A lot more control.</h2><div class="theme-switch" role="group" aria-label="Color theme"><button type="button" data-theme-choice="dark" aria-label="Dark mode">${icon('moon')}<span>Dark</span></button><button type="button" data-theme-choice="light" aria-label="Light mode">${icon('sun')}<span>Light</span></button></div></div><p class="nav-label">WORKSPACE</p><div id="sidebar-nav-slot"></div><div class="sidebar-bottom"><div class="sidebar-note"><span class="status-dot"></span><div><strong>Fixed deposit workspace</strong><small>Accounts, activity & insights</small></div></div><div class="lab-label">BANKING TECHNOLOGY LAB <span>DEMO</span></div></div>`;
  aside.querySelector('#sidebar-nav-slot').replaceWith(navigation);
  navigation.setAttribute('aria-label', 'Main navigation');
  const order = ['reports.html', 'index.html', 'withdraw.html', 'operations.html'];
  const navIcons = {'reports.html':'overview','index.html':'account','withdraw.html':'withdraw','operations.html':'operations'};
  [...navigation.querySelectorAll('a')].sort((a,b) => order.indexOf(a.getAttribute('href').split('?')[0])-order.indexOf(b.getAttribute('href').split('?')[0])).forEach(link => {
    const destination = link.getAttribute('href').split('?')[0];
    link.insertAdjacentHTML('afterbegin',icon(navIcons[destination] || 'account'));
    navigation.append(link);
  });
  const workspace = document.createElement('div'); workspace.className = 'app-workspace';
  const header = document.createElement('header'); header.className = 'app-topbar';
  header.innerHTML = `<div class="topbar-location"><button class="menu-toggle icon-button" type="button" aria-label="Open navigation" aria-controls="app-sidebar" aria-expanded="false">${icon('menu')}</button><span class="breadcrumb-root">Workspace</span><span class="breadcrumb-divider">/</span><span>${titles[file] || 'Fixed deposits'}</span></div><div class="topbar-actions"><span class="workspace-chip"><span class="status-dot"></span>Local lab demo</span><a class="button primary header-create" href="${linkTo('index.html')}">${icon('plus')}<span>Open deposit</span></a></div>`;
  document.body.prepend(aside, workspace); workspace.append(header, main);
  const overlay = document.createElement('button'); overlay.type='button'; overlay.className='nav-overlay'; overlay.tabIndex=-1; overlay.setAttribute('aria-label','Close navigation'); document.body.append(overlay);
  const toggle = header.querySelector('.menu-toggle'); const small = matchMedia('(max-width: 900px)');
  function closeMenu() { document.body.classList.remove('nav-open'); workspace.inert=false; aside.inert=small.matches; toggle.setAttribute('aria-expanded','false'); }
  toggle.addEventListener('click',()=>{ document.body.classList.add('nav-open'); aside.inert=false; workspace.inert=true; toggle.setAttribute('aria-expanded','true'); aside.querySelector('.sidebar-close').focus(); });
  function closeAndFocus() { closeMenu();toggle.focus(); }
  overlay.addEventListener('click',closeAndFocus);aside.querySelector('.sidebar-close').addEventListener('click',closeAndFocus);
  document.addEventListener('keydown',event=>{
    if(!document.body.classList.contains('nav-open'))return;
    if(event.key==='Escape'){event.preventDefault();closeAndFocus();}
    if(event.key==='Tab'){
      const elements=[...aside.querySelectorAll('a,button')].filter(el=>el.getClientRects().length);
      const first=elements[0],last=elements.at(-1);
      if(event.shiftKey && document.activeElement===first){event.preventDefault();last.focus();}
      else if(!event.shiftKey && document.activeElement===last){event.preventDefault();first.focus();}
    }
  });
  small.addEventListener('change',closeMenu);closeMenu();
  aside.querySelectorAll('[data-theme-choice]').forEach(button=>button.addEventListener('click',()=>window.FDTheme(button.dataset.themeChoice,true)));
  window.FDTheme(document.documentElement.dataset.theme,false);
  // Make each page's existing title and description one consistent visual unit.
  const heading=main.querySelector('h1'); const subtitle=main.querySelector('.subtitle');
  const pageHeader=document.createElement('div');pageHeader.className='page-heading';heading.before(pageHeader);pageHeader.append(heading);if(subtitle)pageHeader.append(subtitle);
  main.classList.add(file.replace('.html','')+'-page');
})();

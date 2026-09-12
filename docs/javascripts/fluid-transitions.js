// Fluid scroll reveal & instant navigation lifecycle enhancement
(function() {
  'use strict';

  function initScrollReveal() {
    if (!('IntersectionObserver' in window)) return;

    var targets = document.querySelectorAll(
      '.md-typeset .grid > *, .md-typeset details, .md-typeset table:not([class]), .mermaid'
    );

    var observer = new IntersectionObserver(function(entries) {
      entries.forEach(function(entry) {
        if (entry.isIntersecting) {
          entry.target.classList.add('in-view');
        }
      });
    }, {
      rootMargin: '0px 0px -40px 0px',
      threshold: 0.08
    });

    targets.forEach(function(el) {
      el.classList.add('fade-in-element');
      observer.observe(el);
    });
  }

  // Hook into MkDocs Material document$ observable for instant navigation
  if (typeof document$ !== 'undefined') {
    document$.subscribe(function() {
      initScrollReveal();
    });
  } else {
    document.addEventListener('DOMContentLoaded', initScrollReveal);
  }
})();

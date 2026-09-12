// Spector Documentation — Interactive Mermaid Zoom, Pan & Fullscreen Modal
(function () {
  'use strict';

  // 1. Defense-in-depth: hook Element.prototype.attachShadow
  if (!window.__spectorShadowHooked) {
    window.__spectorShadowHooked = true;
    var orig = Element.prototype.attachShadow;
    if (orig) {
      Element.prototype.attachShadow = function (init) {
        var root = orig.call(this, { mode: 'open' });
        this._spectorShadowRoot = root;
        return root;
      };
    }
  }

  var diagramStates = new WeakMap();
  var activeModal = null;

  function getSvgFromContainer(container) {
    if (!container) return null;
    if (container.shadowRoot) {
      var s = container.shadowRoot.querySelector('svg');
      if (s) return s;
    }
    if (container._spectorShadowRoot) {
      var s2 = container._spectorShadowRoot.querySelector('svg');
      if (s2) return s2;
    }
    return container.querySelector('svg');
  }

  function setupMermaidDiagram(container) {
    if (!container || container.dataset.mermaidZoomed === 'true') return;

    var svg = getSvgFromContainer(container);
    if (!svg) {
      // SVG not yet rendered inside shadow root or container; observe for changes
      var root = container.shadowRoot || container._spectorShadowRoot;
      if (root && !container._spectorObservingRoot) {
        container._spectorObservingRoot = true;
        var rootObs = new MutationObserver(function () {
          var foundSvg = getSvgFromContainer(container);
          if (foundSvg) {
            rootObs.disconnect();
            setupMermaidDiagram(container);
          }
        });
        rootObs.observe(root, { childList: true, subtree: true });
      }
      return;
    }

    container.dataset.mermaidZoomed = 'true';

    // Wrap container inside a dedicated interactive wrapper if not already wrapped
    var wrapper = container.parentElement;
    if (!wrapper || !wrapper.classList.contains('mermaid-interactive-wrapper')) {
      wrapper = document.createElement('div');
      wrapper.className = 'mermaid-interactive-wrapper';
      container.parentNode.insertBefore(wrapper, container);
      wrapper.appendChild(container);
    }

    // Skip if toolbar already exists in wrapper
    if (wrapper.querySelector('.mermaid-toolbar')) return;

    var state = { scale: 1.0, translateX: 0, translateY: 0 };
    diagramStates.set(container, state);

    var toolbar = document.createElement('div');
    toolbar.className = 'mermaid-toolbar';
    toolbar.innerHTML =
      '<span class="mermaid-toolbar-label">DIAGRAM</span>' +
      '<div class="mermaid-toolbar-divider"></div>' +
      '<button type="button" class="mermaid-tool-btn" data-act="zoom-in" title="Zoom In">' +
      '<svg viewBox="0 0 24 24" width="16" height="16"><path fill="currentColor" d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"/></svg>' +
      '</button>' +
      '<button type="button" class="mermaid-tool-btn" data-act="zoom-out" title="Zoom Out">' +
      '<svg viewBox="0 0 24 24" width="16" height="16"><path fill="currentColor" d="M19 13H5v-2h14v2z"/></svg>' +
      '</button>' +
      '<button type="button" class="mermaid-tool-btn" data-act="reset" title="Reset Zoom">' +
      '<svg viewBox="0 0 24 24" width="16" height="16"><path fill="currentColor" d="M12 5V1L7 6l5 5V7c3.31 0 6 2.69 6 6s-2.69 6-6 6-6-2.69-6-6H4c0 4.42 3.58 8 8 8s8-3.58 8-8-3.58-8-8-8z"/></svg>' +
      '</button>' +
      '<button type="button" class="mermaid-tool-btn mermaid-tool-btn--primary" data-act="fullscreen" title="Maximize (Fullscreen)">' +
      '<svg viewBox="0 0 24 24" width="16" height="16"><path fill="currentColor" d="M7 14H5v5h5v-2H7v-3zm-2-4h2V7h3V5H5v5zm12 7h-3v2h5v-5h-2v3zM14 5v2h3v3h2V5h-5z"/></svg>' +
      '</button>';

    wrapper.appendChild(toolbar);

    function updateTransform() {
      var currentSvg = getSvgFromContainer(container);
      if (currentSvg) {
        currentSvg.style.transform =
          'translate(' + state.translateX + 'px, ' + state.translateY + 'px) scale(' + state.scale + ')';
        currentSvg.style.transformOrigin = 'center top';
        currentSvg.style.transition = 'transform 0.22s cubic-bezier(0.16, 1, 0.3, 1)';
      }
    }

    toolbar.addEventListener('click', function (e) {
      var btn = e.target.closest('button');
      if (!btn) return;
      var act = btn.getAttribute('data-act');

      if (act === 'zoom-in') {
        state.scale = Math.min(state.scale + 0.25, 3.5);
        updateTransform();
      } else if (act === 'zoom-out') {
        state.scale = Math.max(state.scale - 0.25, 0.4);
        updateTransform();
      } else if (act === 'reset') {
        state.scale = 1.0;
        state.translateX = 0;
        state.translateY = 0;
        updateTransform();
      } else if (act === 'fullscreen') {
        var currentSvg = getSvgFromContainer(container);
        if (currentSvg) {
          openFullscreenModal(currentSvg);
        }
      }
    });
  }

  function openFullscreenModal(sourceSvg) {
    if (activeModal) closeModal();

    var modal = document.createElement('div');
    modal.className = 'mermaid-modal-backdrop';

    var modalScale = 1.0;
    var modalTranslateX = 0;
    var modalTranslateY = 0;
    var isDragging = false;
    var startX = 0;
    var startY = 0;

    modal.innerHTML =
      '<div class="mermaid-modal-dialog">' +
        '<div class="mermaid-modal-header">' +
          '<div class="mermaid-modal-title">' +
            '<span class="mermaid-modal-badge">MERMAID</span>' +
            '<span>Interactive Diagram Viewer</span>' +
          '</div>' +
          '<div class="mermaid-modal-actions">' +
            '<button type="button" class="mermaid-tool-btn" data-modal-act="zoom-in" title="Zoom In">' +
              '<svg viewBox="0 0 24 24" width="18" height="18"><path fill="currentColor" d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"/></svg>' +
            '</button>' +
            '<button type="button" class="mermaid-tool-btn" data-modal-act="zoom-out" title="Zoom Out">' +
              '<svg viewBox="0 0 24 24" width="18" height="18"><path fill="currentColor" d="M19 13H5v-2h14v2z"/></svg>' +
            '</button>' +
            '<button type="button" class="mermaid-tool-btn" data-modal-act="reset" title="Reset">' +
              '<svg viewBox="0 0 24 24" width="18" height="18"><path fill="currentColor" d="M12 5V1L7 6l5 5V7c3.31 0 6 2.69 6 6s-2.69 6-6 6-6-2.69-6-6H4c0 4.42 3.58 8 8 8s8-3.58 8-8-3.58-8-8-8z"/></svg>' +
            '</button>' +
            '<button type="button" class="mermaid-tool-btn mermaid-tool-btn--close" data-modal-act="close" title="Close (ESC)">' +
              '<svg viewBox="0 0 24 24" width="18" height="18"><path fill="currentColor" d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"/></svg>' +
            '</button>' +
          '</div>' +
        '</div>' +
        '<div class="mermaid-modal-viewport">' +
          '<div class="mermaid-modal-canvas"></div>' +
        '</div>' +
        '<div class="mermaid-modal-footer">' +
          '<span>Scroll mouse wheel to zoom • Click & drag to pan • Press ESC to exit</span>' +
        '</div>' +
      '</div>';

    document.body.appendChild(modal);
    document.body.style.overflow = 'hidden';
    activeModal = modal;

    var canvas = modal.querySelector('.mermaid-modal-canvas');
    var clonedSvg = sourceSvg.cloneNode(true);
    clonedSvg.style.transform = '';
    clonedSvg.style.maxWidth = 'none';
    clonedSvg.style.maxHeight = 'none';
    clonedSvg.style.width = 'auto';
    clonedSvg.style.height = 'auto';
    canvas.appendChild(clonedSvg);

    function updateModalTransform(smooth) {
      canvas.style.transform =
        'translate(' + modalTranslateX + 'px, ' + modalTranslateY + 'px) scale(' + modalScale + ')';
      canvas.style.transition = smooth ? 'transform 0.2s cubic-bezier(0.16, 1, 0.3, 1)' : 'none';
    }

    modal.querySelector('.mermaid-modal-actions').addEventListener('click', function (e) {
      var btn = e.target.closest('button');
      if (!btn) return;
      var act = btn.getAttribute('data-modal-act');
      if (act === 'zoom-in') {
        modalScale = Math.min(modalScale * 1.25, 5.0);
        updateModalTransform(true);
      } else if (act === 'zoom-out') {
        modalScale = Math.max(modalScale / 1.25, 0.3);
        updateModalTransform(true);
      } else if (act === 'reset') {
        modalScale = 1.0;
        modalTranslateX = 0;
        modalTranslateY = 0;
        updateModalTransform(true);
      } else if (act === 'close') {
        closeModal();
      }
    });

    modal.addEventListener('click', function (e) {
      if (e.target === modal) closeModal();
    });

    var viewport = modal.querySelector('.mermaid-modal-viewport');
    viewport.addEventListener('wheel', function (e) {
      e.preventDefault();
      var delta = e.deltaY < 0 ? 1.15 : 0.87;
      modalScale = Math.min(Math.max(modalScale * delta, 0.3), 5.0);
      updateModalTransform(false);
    }, { passive: false });

    viewport.addEventListener('mousedown', function (e) {
      if (e.button !== 0) return;
      isDragging = true;
      startX = e.clientX - modalTranslateX;
      startY = e.clientY - modalTranslateY;
      viewport.style.cursor = 'grabbing';
    });

    function onMouseMove(e) {
      if (!isDragging) return;
      modalTranslateX = e.clientX - startX;
      modalTranslateY = e.clientY - startY;
      updateModalTransform(false);
    }

    function onMouseUp() {
      if (isDragging) {
        isDragging = false;
        if (viewport) viewport.style.cursor = 'grab';
      }
    }

    window.addEventListener('mousemove', onMouseMove);
    window.addEventListener('mouseup', onMouseUp);

    function onKeyDown(e) {
      if (e.key === 'Escape') closeModal();
    }
    window.addEventListener('keydown', onKeyDown);

    modal._cleanup = function () {
      window.removeEventListener('keydown', onKeyDown);
      window.removeEventListener('mousemove', onMouseMove);
      window.removeEventListener('mouseup', onMouseUp);
    };
  }

  function closeModal() {
    if (!activeModal) return;
    if (activeModal._cleanup) activeModal._cleanup();
    activeModal.classList.add('closing');
    setTimeout(function () {
      if (activeModal && activeModal.parentNode) {
        activeModal.parentNode.removeChild(activeModal);
      }
      activeModal = null;
      document.body.style.overflow = '';
    }, 200);
  }

  function scanAllMermaid() {
    var containers = document.querySelectorAll('.mermaid');
    containers.forEach(function (c) {
      setupMermaidDiagram(c);
    });
  }

  // Observe dynamically inserted elements (Material for MkDocs instant loading and async rendering)
  var observer = new MutationObserver(function (mutations) {
    for (var i = 0; i < mutations.length; i++) {
      var m = mutations[i];
      for (var j = 0; j < m.addedNodes.length; j++) {
        var node = m.addedNodes[j];
        if (node.nodeType === 1) {
          if (node.classList && node.classList.contains('mermaid')) {
            setupMermaidDiagram(node);
          } else if (node.querySelectorAll) {
            var found = node.querySelectorAll('.mermaid');
            if (found.length) {
              found.forEach(setupMermaidDiagram);
            }
          }
        }
      }
    }
  });

  if (document.body) {
    observer.observe(document.body, { childList: true, subtree: true });
  } else {
    document.addEventListener('DOMContentLoaded', function () {
      observer.observe(document.body, { childList: true, subtree: true });
    });
  }

  // Tab click listener for MkDocs content tabs
  document.addEventListener('change', function (e) {
    if (e.target && e.target.type === 'radio') {
      setTimeout(scanAllMermaid, 60);
    }
  });

  if (typeof document$ !== 'undefined') {
    document$.subscribe(scanAllMermaid);
  } else {
    document.addEventListener('DOMContentLoaded', scanAllMermaid);
  }

  // Polling to catch async script loading from unpkg
  var pollInterval = setInterval(scanAllMermaid, 200);
  setTimeout(function () {
    clearInterval(pollInterval);
  }, 6000);
})();

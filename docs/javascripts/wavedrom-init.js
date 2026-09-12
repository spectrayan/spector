// Initialize WaveDrom diagrams after page load
document.addEventListener('DOMContentLoaded', function() {
  if (typeof WaveDrom !== 'undefined') {
    WaveDrom.ProcessAll();
  }
});

// Also handle MkDocs instant navigation (SPA mode)
if (typeof document$ !== 'undefined') {
  document$.subscribe(function() {
    if (typeof WaveDrom !== 'undefined') {
      WaveDrom.ProcessAll();
    }
  });
}

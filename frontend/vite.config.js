import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// The built SPA is copied into Spring's static resources and served at the root,
// so assets resolve from absolute paths under "/".
export default defineConfig({
  plugins: [react()],
  build: {
    outDir: 'dist',
    emptyOutDir: true,
  },
});

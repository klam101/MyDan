import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// The cloud frontend runs on mock data for now, so there is no API proxy.
export default defineConfig({
  plugins: [react()],
});

import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  base: "/Hellhound/",
  build: {
    target: "es2022"
  }
});

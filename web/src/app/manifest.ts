import type { MetadataRoute } from "next";

// Colours are sRGB approximations of docs/design tokens --brand and --bg (light).
export default function manifest(): MetadataRoute.Manifest {
  return {
    name: "PaddleUp",
    short_name: "PaddleUp",
    description: "Private player auctions and fantasy leagues for friends.",
    start_url: "/",
    display: "standalone",
    theme_color: "#e0532f",
    background_color: "#faf6f1",
    icons: [
      { src: "/icons/icon-192.png", sizes: "192x192", type: "image/png" },
      { src: "/icons/icon-512.png", sizes: "512x512", type: "image/png" },
      {
        src: "/icons/icon-maskable-512.png",
        sizes: "512x512",
        type: "image/png",
        purpose: "maskable",
      },
    ],
  };
}

import type { Metadata } from "next";
import { DM_Mono, Plus_Jakarta_Sans } from "next/font/google";
import type { ReactNode } from "react";
import "../styles/tokens.css";
import "./globals.css";
import { SwRegister } from "./sw-register";

const jakarta = Plus_Jakarta_Sans({
  subsets: ["latin"],
  weight: ["400", "500", "600", "700", "800"],
  variable: "--font-sans",
});

const dmMono = DM_Mono({
  subsets: ["latin"],
  weight: ["400", "500"],
  variable: "--font-mono",
});

export const metadata: Metadata = {
  title: "PaddleUp",
  description: "Private player auctions and fantasy leagues for friends.",
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="en" className={`${jakarta.variable} ${dmMono.variable}`}>
      <head>
        {/* Icons per docs/design/README.md: Material Symbols Rounded, FILL 0 default, 1 when active.
            display=block (as in the mocks) so icon ligature names never flash as text. The App Router
            root layout covers every page, so the pages/_document rule does not apply here. */}
        {/* eslint-disable-next-line @next/next/google-font-display, @next/next/no-page-custom-font */}
        <link
          rel="stylesheet"
          href="https://fonts.googleapis.com/css2?family=Material+Symbols+Rounded:opsz,wght,FILL,GRAD@20..48,400..600,0..1,0&display=block"
        />
      </head>
      <body>
        {children}
        <SwRegister />
      </body>
    </html>
  );
}

import type { Metadata } from "next";
import type { ReactNode } from "react";
import "./globals.css";
import { SwRegister } from "./sw-register";

export const metadata: Metadata = {
  title: "PaddleUp",
  description: "Private player auctions and fantasy leagues for friends.",
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="en">
      <body>
        {children}
        <SwRegister />
      </body>
    </html>
  );
}

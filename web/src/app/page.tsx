import { redirect } from "next/navigation";
import { HOME } from "@/auth/redirect";

// Middleware has already sent signed-out visitors to sign-in. Until the rooms list exists, the
// profile is the signed-in home.
export default function Home() {
  redirect(HOME);
}

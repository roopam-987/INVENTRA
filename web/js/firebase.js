import { initializeApp } from "https://www.gstatic.com/firebasejs/11.10.0/firebase-app.js";
import {
  getAuth,
  GoogleAuthProvider,
  signInWithPopup,
  onAuthStateChanged,
  signOut
} from "https://www.gstatic.com/firebasejs/11.10.0/firebase-auth.js";

const firebaseConfig = {
  apiKey: "AIzaSyDbhiNyqpTlTjwShvpmwO-6FsKK8_bNJg8",
  authDomain: "inventra-2e43e.firebaseapp.com",
  projectId: "inventra-2e43e",
  storageBucket: "inventra-2e43e.firebasestorage.app",
  messagingSenderId: "631221054551",
  appId: "1:631221054551:web:21da2f83188ec733a90712"
};

const app = initializeApp(firebaseConfig);

export const auth = getAuth(app);
export const provider = new GoogleAuthProvider();

export async function loginWithGoogle() {
  const result = await signInWithPopup(auth, provider);
  return result.user;
}

export async function getToken() {
  if (!auth.currentUser) throw new Error("Please sign in first.");
  return await auth.currentUser.getIdToken();
}

export function watchAuth(callback) {
  return onAuthStateChanged(auth, callback);
}

export async function logout() {
  await signOut(auth);
}
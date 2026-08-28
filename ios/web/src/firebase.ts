import { initializeApp } from 'firebase/app'
import { getAuth } from 'firebase/auth'
import { getFirestore } from 'firebase/firestore'

// Exported (not just used locally) so `lib/createUserAccount.ts` can spin
// up a secondary, throwaway `FirebaseApp` instance with the same project
// config — see that file for why.
export const firebaseConfig = {
  apiKey: 'AIzaSyBN-XwH5NZXnKL94EDBMgW4Mw47mtwDWGw',
  authDomain: 'myfirstapp-b53ca.firebaseapp.com',
  projectId: 'myfirstapp-b53ca',
  storageBucket: 'myfirstapp-b53ca.firebasestorage.app',
  messagingSenderId: '760336719680',
  appId: '1:760336719680:web:780cb64fdbd3293739e4a5',
  measurementId: 'G-EX0DLFBJ88',
}

const app = initializeApp(firebaseConfig)
export const auth = getAuth(app)
export const db = getFirestore(app)

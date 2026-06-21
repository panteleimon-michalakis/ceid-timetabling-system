import axios from 'axios';

// Account-less axios instance για τη δημόσια προβολή (/public).
// ΚΑΝΕΝΑ request interceptor (no token) και ΚΑΝΕΝΑ response interceptor — κρίσιμο:
// αποφεύγει το `401 → redirect /login` του authed `api`, ώστε ένας επισκέπτης
// χωρίς λογαριασμό να μη πετιέται στο login.
const publicApi = axios.create({
  baseURL: import.meta.env.VITE_API_URL ?? 'http://localhost:8080/api',
});

export default publicApi;

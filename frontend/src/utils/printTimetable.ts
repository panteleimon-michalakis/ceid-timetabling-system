/**
 * Κοινό shell εκτύπωσης για τα προγράμματα (εβδομαδιαίο & εξεταστική) — Φ5a.
 * Εξάγει το boilerplate που ήταν διπλογραμμένο σε WeeklyTimetable/ExamTimetable
 * ώστε το επόμενο βήμα (print dialog + grouping) να ακουμπά ΜΙΑ πηγή.
 * ΚΡΙΣΙΜΟ: αμιγώς refactor — το παραγόμενο HTML μένει σημασιολογικά ίδιο.
 */

/** HTML-escape για & < > " (ίδιος escaper με πριν). */
export function esc(s: unknown): string {
  return String(s ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

/** Κωδικός μαθήματος όπως στο πρότυπο τμήματος: χωρίς το «CEID_» prefix (μόνο στις εκτυπώσεις). */
export function shortCode(code?: string | null): string {
  return String(code ?? '').replace(/^CEID[_\s-]?/i, '');
}

/** Χρώματα ανά έτος σπουδών (1ο…5ο) για τις εκτυπώσεις (πρώην `YC`). */
export const YEAR_COLORS = ['#2563eb', '#059669', '#7c3aed', '#d97706', '#dc2626'];

/** Χρώματα/ετικέτες ανά τύπο μαθήματος (πρώην `TC`) — μόνο το εβδομαδιαίο το χρησιμοποιεί. */
export const TYPE_COLORS: Record<string, { bg: string; border: string; label: string }> = {
  LECTURE:  { bg: '#eff6ff', border: '#2563eb', label: 'Θ' },
  TUTORIAL: { bg: '#f0fdf4', border: '#16a34a', label: 'Φ' },
  LAB:      { bg: '#fffbeb', border: '#d97706', label: 'Ε' },
};

/** Όλες οι ώρες έναρξης (09:00…20:00). */
export const ALL_HOURS = ['09:00', '10:00', '11:00', '12:00', '13:00', '14:00', '15:00', '16:00', '17:00', '18:00', '19:00', '20:00'];

/** Χρώμα έτους με ασφαλές fallback (ίδιο με το πρώην inline `YC[...] ?? '#2563eb'`). */
export function yearColor(studyYear?: number): string {
  return YEAR_COLORS[(studyYear ?? 1) - 1] ?? '#2563eb';
}

export interface PrintDocumentOptions {
  /** Τίτλος <title> (raw — γίνεται escape μέσα στο shell). */
  title: string;
  /** HTML κεφαλίδας (π.χ. το `.hdr` block) — μπαίνει αμέσως μετά το hint. */
  headerHtml: string;
  /** Κυρίως σώμα (π.χ. ο πίνακας/οι πίνακες). */
  bodyHtml: string;
  /** Μέγεθος `.hdr h1` σε pt (weekly 13, exam 12). */
  h1FontSizePt?: number;
  /** `gap` του `.legend` σε px (weekly 12, exam 10). */
  legendGapPx?: number;
  /** `flex-wrap` στο `.legend` (weekly true, exam false). */
  legendWrap?: boolean;
}

/**
 * Χτίζει το πλήρες έγγραφο εκτύπωσης (`<!DOCTYPE html>…</html>`) με το κοινό
 * `<style>` + `.hint` warning div, και μετά `${headerHtml}${bodyHtml}`.
 * Οι μικροδιαφορές weekly/exam περνούν ως παράμετροι ώστε το output να μένει ίδιο.
 */
export function buildPrintDocument(opts: PrintDocumentOptions): string {
  const { title, headerHtml, bodyHtml, h1FontSizePt = 13, legendGapPx = 12, legendWrap = true } = opts;
  return `<!DOCTYPE html><html lang="el"><head><meta charset="UTF-8">
      <title>${esc(title)}</title>
      <style>
        *{box-sizing:border-box;margin:0;padding:0;}
        body{font-family:Arial,sans-serif;font-size:9pt;}
        @page{size:297mm 210mm;margin:8mm;}
        @media print{body{-webkit-print-color-adjust:exact;print-color-adjust:exact;}}
        table{border-collapse:collapse;width:100%;}
        .hdr{margin-bottom:8px;border-bottom:2px solid #1e40af;padding-bottom:5px;}
        .hdr h1{font-size:${h1FontSizePt}pt;color:#1e40af;} .hdr p{font-size:8pt;color:#64748b;}
        .legend{display:flex;gap:${legendGapPx}px;margin-top:5px;font-size:7.5pt;${legendWrap ? 'flex-wrap:wrap;' : ''}}
        .ld{display:flex;align-items:center;gap:3px;}
        .ldot{width:9px;height:9px;border-radius:2px;}
        @media screen{.hint{background:#fef3c7;border:1px solid #d97706;border-radius:4px;padding:8px 14px;margin-bottom:10px;font-size:10pt;}}
        @media print{.hint{display:none!important;}}
      </style></head><body><div class="hint">⚠️ Για σωστή εκτύπωση: στο πεδίο <strong>Προορισμός</strong> επίλεξε <strong>"Αποθήκευση ως PDF"</strong> (όχι Microsoft Print to PDF) — ή επίλεξε <strong>Διάταξη → Οριζόντιος</strong>.</div>${headerHtml}${bodyHtml}</body></html>`;
}

/** Σημερινή ημερομηνία ως `dd/mm/yyyy` (footer εκτυπώσεων, aSc-style πρότυπο). */
export function todayGreek(): string {
  const d = new Date();
  const dd = String(d.getDate()).padStart(2, '0');
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  return `${dd}/${mm}/${d.getFullYear()}`;
}

/** Άνοιγμα νέου παραθύρου + εκτύπωση (ίδιο boilerplate με πριν). */
export function openAndPrint(html: string): void {
  const win = window.open('', '_blank', 'width=1200,height=800');
  if (!win) { alert('Επέτρεψε τα pop-ups του browser για εκτύπωση.'); return; }
  win.document.write(html); win.document.close(); win.onload = () => win.print();
}

// ─── Φ5b: grouping (μία οντότητα/σελίδα) ────────────────────────────────────

export type PrintGroupBy = 'semester' | 'room' | 'teacher';

export interface PrintGroup<T> { key: string; title: string; items: T[]; }

/** Parse του derived teachersText (CSV) → μοναδικά ονόματα. Φ2: derived από το canonical M2M. */
export function parseTeachers(teachersText?: string | null): string[] {
  if (!teachersText) return [];
  return Array.from(new Set(
    teachersText.split(/[,;]/).map(s => s.trim()).filter(Boolean)
  ));
}

/**
 * Ομαδοποίηση: επιστρέφει ordered groups. Ένα item μπορεί να ανήκει σε ΠΟΛΛΑ groups
 * (π.χ. μάθημα με 2 διδάσκοντες → εμφανίζεται σε 2 teacher-pages).
 * keysOf(item) → λίστα {key,title,sortKey} στα οποία ανήκει το item.
 * Τα groups ταξινομούνται με sortKey (string compare, locale 'el').
 */
export function groupItems<T>(
  items: T[],
  keysOf: (item: T) => { key: string; title: string; sortKey: string }[]
): PrintGroup<T>[] {
  const map = new Map<string, { title: string; sortKey: string; items: T[] }>();
  for (const it of items) {
    for (const { key, title, sortKey } of keysOf(it)) {
      if (!map.has(key)) map.set(key, { title, sortKey, items: [] });
      map.get(key)!.items.push(it);
    }
  }
  return Array.from(map.entries())
    .sort((a, b) => a[1].sortKey.localeCompare(b[1].sortKey, 'el'))
    .map(([key, v]) => ({ key, title: v.title, items: v.items }));
}

// ─── Φ5c-3: seasonal electives split (Χειμερινού/Εαρινού/λοιπά) ──────────────

/**
 * Bucket «Μαθήματα Επιλογής» για μη-REQUIRED μάθημα, βάσει `course.semesterType`.
 * sortKeys 97/98/99 → πάντα ΜΕΤΑ τα εξάμηνα (`sem-${n}`), με σειρά Χειμ→Εαρ→λοιπά.
 * Κοινό σε weekly+exam ώστε keysOf↔printAvailable keys να ταιριάζουν πάντα.
 */
export function electiveBucket(semesterType?: string | null): { key: string; title: string; sortKey: string } {
  if (semesterType === 'FALL')   return { key: 'electives-fall',   title: 'Μαθήματα Επιλογής Χειμερινού', sortKey: '97' };
  if (semesterType === 'SPRING') return { key: 'electives-spring', title: 'Μαθήματα Επιλογής Εαρινού',     sortKey: '98' };
  return { key: 'electives', title: 'Μαθήματα Επιλογής', sortKey: '99' };
}

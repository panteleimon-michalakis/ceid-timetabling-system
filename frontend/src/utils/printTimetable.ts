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

/** Άνοιγμα νέου παραθύρου + εκτύπωση (ίδιο boilerplate με πριν). */
export function openAndPrint(html: string): void {
  const win = window.open('', '_blank', 'width=1200,height=800');
  if (!win) { alert('Επέτρεψε τα pop-ups του browser για εκτύπωση.'); return; }
  win.document.write(html); win.document.close(); win.onload = () => win.print();
}

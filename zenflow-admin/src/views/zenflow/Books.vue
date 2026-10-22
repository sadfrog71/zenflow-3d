<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { api, errorText as genericErrorText } from '../../api/zenflow/client';

type Book = {
  id: string; title: string; author: string; publisher: string; sourceLanguage: string; sourceVersion: string;
  sourceUrl: string; category: string; summary: string; rightsHolder: string; licenseName: string;
  licenseEvidence: string; licenseTerritory: string; licenseTerm: string; licenseExpiresOn: string;
  permissionDisplay: boolean; permissionTranslate: boolean; permissionAdapt: boolean; permissionAi: boolean;
  authorizationVerified: boolean; state: string; version: number; currentSourceVersionId: string | null;
  sourceVersionNo?: number; sourceReviewed?: boolean; chapterCount?: number; latestTranslationStatus?: string;
};
type AnnotationTone = 'word' | 'clause' | 'variant';
type Annotation = { phrase: string; note: string; tone: AnnotationTone };
type Chapter = {
  id: string; chapterNo: number; title: string; sourceText: string; translationText: string;
  commentary: string; annotations: Annotation[]; translationState: string; reviewState: string;
  failureReason?: string; qaReport?: { severity: string; code: string; message: string }[]; included: boolean;
};
type Translation = {
  id: string; sourceVersionId: string; sourceVersionNo: number; targetLanguage: string; revisionNo: number;
  status: string; version: number; isExcerpt: boolean; excerptLabel: string; qaHardIssues: number;
  qaReport?: { chapters?: { chapterNo: number; issues: { severity: string; code: string; message: string }[] }[] };
  origin: string; modelNote: string; translatorNote: string;
  uploadedFile?: { filename: string; type: string; sha256: string; createdAt: string } | null;
  chapters: Chapter[];
};

function errorText(error: unknown) {
  const response = (error as { response?: { data?: { errorCode?: string; message?: string } } })?.response?.data;
  const code = response?.errorCode || response?.message;
  const labels: Record<string, string> = {
    BOOK_INPUT_INVALID: '请检查书名、版本和书籍信息', BOOK_FILE_TYPE_UNSUPPORTED: '该文件格式暂不支持',
    BOOK_FILE_UNREADABLE: '文件无法读取；扫描版 PDF 请先进行 OCR', BOOK_FILE_NO_TEXT: '未提取到可读正文',
    BOOK_FILE_SIZE_INVALID: '文件为空或超过 30 MB', BOOK_TEXT_LIMIT_EXCEEDED: '提取正文超过 40 万字符，请拆分为多个来源版本',
    BOOK_RIGHTS_INCOMPLETE: '请补齐来源链接并确认产品内展示权限', BOOK_CREATOR_REQUIRED: '请填写作者或发布方',
    BOOK_AUTHORIZATION_REQUIRED: '请先核实书籍授权', BOOK_DISPLAY_PERMISSION_REQUIRED: '许可尚未覆盖产品内展示',
    BOOK_TRANSLATION_PERMISSION_REQUIRED: '许可尚未覆盖翻译和 AI 辅助处理', BOOK_ADAPTATION_PERMISSION_REQUIRED: '许可尚未覆盖白话解说或其他改编',
    BOOK_LICENSE_EXPIRED: '书籍许可已过期', BOOK_SOURCE_REVIEW_REQUIRED: '请先检查并确认来源章节',
    BOOK_SOURCE_VERSION_STALE: '来源版本已变化，请刷新后重新上传译稿', BOOK_SOURCE_VERSION_CONFLICT: '来源版本已变化，请重新加载',
    BOOK_TRANSLATION_LANGUAGE_SAME: '目标语言不能与原文相同', BOOK_TRANSLATION_CHAPTER_ALIGNMENT_REQUIRED: '译稿章节数与所选来源章节不一致；请保留章节标题并按顺序整理',
    BOOK_TRANSLATION_CHAPTERS_INVALID: '章节范围无效或包含重复章节', BOOK_EXCERPT_LABEL_REQUIRED: '节选译稿必须填写节选名称',
    BOOK_TRANSLATION_REVIEW_REQUIRED: '质检或人工校对尚未完成，暂不能上架', BOOK_QUALITY_CHECK_REQUIRED: '请先运行章节质检',
    BOOK_TRANSLATION_IMMUTABLE: '此版本已归档，不能直接修改', BOOK_VERSION_CONFLICT: '书目信息已变化，请刷新后重试',
    BOOK_TRANSLATION_INPUT_INVALID: '请检查译稿文件、目标语言和章节范围', BOOK_TRANSLATION_ASSET_NOT_FOUND: '此版本没有可下载的本地原始译稿',
    BOOK_SOURCE_EMPTY: '来源版本中没有可翻译章节', BOOK_TRANSLATION_BUSY: '翻译队列繁忙，请稍后重试', BOOK_TRANSLATION_FAILED: '章节翻译失败，可在译稿页重试',
    BOOK_ANNOTATIONS_INVALID: '夹注内容或类型无效，请检查释词、释句和异文标记',
  };
  return code && labels[code] ? labels[code] : genericErrorText(error);
}

const books = ref<Book[]>([]), busy = ref(false), activeBook = ref<Book | null>(null);
const detail = ref<(Book & { sourceChapters: { id: string; chapterNo: number; title: string; sourceText: string }[]; translations: Translation[] }) | null>(null);
const translation = ref<Translation | null>(null), sourceEditor = ref(false), uploadDialog = ref(false), translationDialog = ref(false), previewDialog = ref(false), rightsOpen = ref(false);
const sourceFileInput = ref<HTMLInputElement>(), translationFileInput = ref<HTMLInputElement>(), annotationsFileInput = ref<HTMLInputElement>();
const sourceFile = ref<File | null>(null), translationFile = ref<File | null>(null);
const sourceChapters = ref<{ id?: string; chapterNo: number; title: string; sourceText: string }[]>([]);
const selectedChapter = ref<Chapter | null>(null), chapterDialog = ref(false), annotations = ref<Annotation[]>([]);
const chapterForm = reactive({ translationText: '', commentary: '' });
const chapterNumbers = ref('');
const bookForm = reactive({
  title: '', author: '', publisher: '', sourceLanguage: 'en-US', sourceVersion: '1.0', sourceUrl: '', category: 'classics', summary: '',
  rightsHolder: '', licenseName: '', licenseEvidence: '', licenseTerritory: '', licenseTerm: '', licenseExpiresOn: '',
  permissionDisplay: false, permissionTranslate: false, permissionAdapt: false, permissionAi: false, authorizationVerified: false,
});
const rightsForm = reactive({ ...bookForm });
function missingRightsFields(form: typeof bookForm): string[] {
  if (!form.authorizationVerified) return [];
  const required = {
    sourceUrl: '来源链接',
  } as const;
  const missing: string[] = Object.entries(required).filter(([key]) => !String(form[key as keyof typeof required] || '').trim()).map(([, label]) => label);
  if (!form.permissionDisplay) missing.push('产品内展示权限');
  return missing;
}
function validateRights(form: typeof bookForm): boolean {
  const missing = missingRightsFields(form);
  if (!missing.length) return true;
  ElMessage.warning('确认授权前，请补齐：' + missing.join('、'));
  return false;
}
const translationForm = reactive({ targetLanguage: 'zh-CN', excerptLabel: '', modelNote: 'Codex（当前会话模型）', translatorNote: '' });

const editable = computed(() => !!translation.value && !['PUBLISHED', 'ARCHIVED', 'REVOKED', 'UNPUBLISHED'].includes(translation.value.status));
const statusLabels: Record<string, string> = {
  DRAFT: '草稿', TRANSLATING: '翻译中', FAILED: '翻译失败', NEEDS_REVIEW: '待校对', QA_FAILED: '质检未通过',
  READY: '待发布', PUBLISHED: '已上架', ARCHIVED: '已归档', UNPUBLISHED: '已下架', REVOKED: '已撤回',
};
const languageLabel = (value: string) => value === 'en-US' ? '英语' : value === 'zh-CN' ? '简体中文' : value;
const statusType = (value: string) => ({ PUBLISHED: 'success', READY: 'success', QA_FAILED: 'danger', FAILED: 'danger', REVOKED: 'danger', TRANSLATING: 'warning', NEEDS_REVIEW: 'warning' }[value] || 'info') as 'success' | 'danger' | 'warning' | 'info';
const canTranslate = computed(() => !!detail.value?.authorizationVerified && !!detail.value.permissionDisplay && !!detail.value.permissionTranslate && !!detail.value.permissionAi && !!detail.value.sourceReviewed);
const canImportLocalDraft = computed(() => !!detail.value?.sourceReviewed && detail.value?.state !== 'REVOKED');

async function load() {
  busy.value = true;
  try { books.value = (await api.get('/books')).data; }
  catch (error) { ElMessage.error(errorText(error)); }
  finally { busy.value = false; }
}
async function openBook(book: Book) {
  busy.value = true; activeBook.value = book; translation.value = null;
  try {
    detail.value = (await api.get('/books/' + book.id)).data;
    sourceChapters.value = detail.value!.sourceChapters.map(chapter => ({ ...chapter }));
    sourceEditor.value = false;
  } catch (error) { ElMessage.error(errorText(error)); }
  finally { busy.value = false; }
}
function newBook() {
  Object.assign(bookForm, { title: '', author: '', publisher: '', sourceLanguage: 'en-US', sourceVersion: '1.0', sourceUrl: '', category: 'classics', summary: '',
    rightsHolder: '', licenseName: '', licenseEvidence: '', licenseTerritory: '', licenseTerm: '', licenseExpiresOn: '', permissionDisplay: false,
    permissionTranslate: false, permissionAdapt: false, permissionAi: false, authorizationVerified: false });
  sourceFile.value = null; uploadDialog.value = true;
}
function chooseSource(event: Event) {
  sourceFile.value = (event.target as HTMLInputElement).files?.[0] || null;
  if (sourceFile.value && !bookForm.title) bookForm.title = sourceFile.value.name.replace(/\.[^.]+$/, '');
}
function chooseTranslation(event: Event) { translationFile.value = (event.target as HTMLInputElement).files?.[0] || null; }
async function importBody(file: File) {
  const bytes = new Uint8Array(await file.arrayBuffer()); let binary = '';
  for (let start = 0; start < bytes.length; start += 8192) binary += String.fromCharCode(...bytes.subarray(start, start + 8192));
  return { filename: file.name, base64: btoa(binary) };
}
async function importBook() {
  if (!validateRights(bookForm)) return;
  if (!sourceFile.value) { ElMessage.warning('请先选择书籍文件'); return; }
  if (sourceFile.value.size > 30 * 1024 * 1024) { ElMessage.warning('文件最大 30 MB'); return; }
  busy.value = true;
  try {
    const file = await importBody(sourceFile.value);
    const created = (await api.post('/books/import', { ...file, ...bookForm })).data as Book;
    uploadDialog.value = false; await load(); await openBook(created);
    ElMessage.success('书籍已导入为后台草稿，请先核对解析内容与授权。');
  } catch (error) { ElMessage.error(errorText(error)); }
  finally { busy.value = false; if (sourceFileInput.value) sourceFileInput.value.value = ''; }
}
function editRights() {
  if (!detail.value) return;
  Object.assign(rightsForm, Object.fromEntries(Object.keys(rightsForm).map(key => [key, (detail.value as any)[key]])));
  rightsOpen.value = true;
}
async function saveRights() {
  if (!detail.value) return;
  if (!validateRights(rightsForm)) return;
  busy.value = true;
  try {
    await api.put('/books/' + detail.value.id, { ...rightsForm, expectedVersion: detail.value.version });
    rightsOpen.value = false; await openBook(detail.value); await load(); ElMessage.success('书目信息和授权已保存');
  } catch (error) { ElMessage.error(errorText(error)); }
  finally { busy.value = false; }
}
async function saveSourceChapters() {
  if (!detail.value) return;
  busy.value = true;
  try {
    await api.put('/books/' + detail.value.id + '/chapters', { expectedSourceVersionId: detail.value.currentSourceVersionId, chapters: sourceChapters.value.map(({ chapterNo, title, sourceText }) => ({ chapterNo, title, sourceText })) });
    await openBook(detail.value); await load(); ElMessage.success('解析章节已保存；请重新核对来源内容。');
  } catch (error) { ElMessage.error(errorText(error)); }
  finally { busy.value = false; }
}
async function reviewSource() {
  if (!detail.value?.currentSourceVersionId) return;
  try { await ElMessageBox.confirm('请确认章节顺序、标题和正文均与所选底本一致。此确认只核对文本，不代表版权或使用许可已核实。', '核对来源文本'); }
  catch { return; }
  busy.value = true;
  try { await api.post('/books/' + detail.value.id + '/source-review', { sourceVersionId: detail.value.currentSourceVersionId, reviewed: true }); await openBook(detail.value); await load(); ElMessage.success('来源文本已核对；版权和许可仍需单独确认'); }
  catch (error) { ElMessage.error(errorText(error)); }
  finally { busy.value = false; }
}
function startTranslation() {
  if (!detail.value) return;
  translationForm.targetLanguage = detail.value.sourceLanguage === 'en-US' ? 'zh-CN' : 'en-US';
  translationForm.excerptLabel = ''; chapterNumbers.value = ''; translationFile.value = null; translationForm.modelNote = 'Codex（当前会话模型）';
  translationForm.translatorNote = ['heart-t251', 'diamond-t235'].some(id => matchesDraftBook(id, detail.value!))
    ? '内部待审工作译稿；由 Codex 模型生成，尚未人工校订。夹注为选词说明，不是完整逐词覆盖。权利与公开使用许可尚待人工核验。'
    : '';
  translationDialog.value = true;
}
function parseChapterNumbers() {
  const raw = chapterNumbers.value.trim();
  return raw ? raw.split(/[\s,，;；]+/).filter(Boolean).map(value => Number(value)) : [];
}
async function createServerTranslation() {
  if (!detail.value) return;
  busy.value = true;
  try {
    await api.post('/books/' + detail.value.id + '/translations', { targetLanguage: translationForm.targetLanguage, chapterNos: parseChapterNumbers(), excerptLabel: translationForm.excerptLabel });
    translationDialog.value = false; await openBook(detail.value); await load(); ElMessage.success('服务器翻译任务已进入队列');
  } catch (error) { ElMessage.error(errorText(error)); }
  finally { busy.value = false; }
}
async function importLocalTranslation() {
  if (!detail.value || !translationFile.value) { ElMessage.warning('请选择本地译稿文件'); return; }
  if (translationFile.value.size > 30 * 1024 * 1024) { ElMessage.warning('文件最大 30 MB'); return; }
  busy.value = true;
  try {
    const file = await importBody(translationFile.value);
    const body = { ...file, targetLanguage: translationForm.targetLanguage, sourceVersionId: detail.value.currentSourceVersionId,
      chapterNos: parseChapterNumbers(), excerptLabel: translationForm.excerptLabel, modelNote: translationForm.modelNote, translatorNote: translationForm.translatorNote };
    const created = (await api.post('/books/' + detail.value.id + '/translations/import', body)).data as Translation;
    translationDialog.value = false; translationFile.value = null; await openBook(detail.value); await load(); await openTranslation(created.id);
    ElMessage.success('本地译稿已归档为新版本；服务器未再次调用翻译模型。请先运行质检并逐章校对。');
  } catch (error) { ElMessage.error(errorText(error)); }
  finally { busy.value = false; if (translationFileInput.value) translationFileInput.value.value = ''; }
}
async function openTranslation(id: string) {
  if (!detail.value) return;
  busy.value = true;
  try { translation.value = (await api.get('/books/' + detail.value.id + '/translations/' + id)).data; }
  catch (error) { ElMessage.error(errorText(error)); }
  finally { busy.value = false; }
}
async function refreshTranslation() { if (translation.value) await openTranslation(translation.value.id); }
async function runQa() {
  if (!detail.value || !translation.value) return;
  busy.value = true;
  try { await api.post(`/books/${detail.value.id}/translations/${translation.value.id}/qa`); await refreshTranslation(); ElMessage.success('章节质检已完成；请检查报告并人工校对。'); }
  catch (error) { ElMessage.error(errorText(error)); }
  finally { busy.value = false; }
}
async function retryTranslation() {
  if (!detail.value || !translation.value) return;
  busy.value = true;
  try { await api.post(`/books/${detail.value.id}/translations/${translation.value.id}/retry`); await refreshTranslation(); ElMessage.success('失败章节已重新进入队列'); }
  catch (error) { ElMessage.error(errorText(error)); }
  finally { busy.value = false; }
}
function editChapter(chapter: Chapter) {
  selectedChapter.value = chapter; chapterForm.translationText = chapter.translationText || ''; chapterForm.commentary = chapter.commentary || '';
  annotations.value = (chapter.annotations || []).map(item => ({ ...item, tone: item.tone || 'word' })); chapterDialog.value = true;
}
async function saveChapter() {
  if (!detail.value || !translation.value || !selectedChapter.value) return;
  busy.value = true;
  try {
    await api.put(`/books/${detail.value.id}/translations/${translation.value.id}/chapters/${selectedChapter.value.id}`, {
      expectedVersion: translation.value.version, translationText: chapterForm.translationText, commentary: chapterForm.commentary, annotations: annotations.value,
    });
    chapterDialog.value = false; await refreshTranslation(); ElMessage.success('译文已保存为待校对修改');
  } catch (error) { ElMessage.error(errorText(error)); }
  finally { busy.value = false; }
}

function matchesDraftBook(bookId: string, book: Book) {
  if (bookId === book.id) return true;
  const source = `${book.title} ${book.sourceUrl} ${book.sourceVersion}`.toLowerCase();
  if (bookId === 'heart-t251') return /0251|heart|心经|心經/.test(source);
  if (bookId === 'diamond-t235') return /0235|vajracchedika|diamond|金刚经|金剛/.test(source);
  return false;
}

async function importAnnotations(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0]; input.value = '';
  if (!file || !detail.value || !translation.value) return;
  if (!editable.value) { ElMessage.warning('当前译稿已锁定，不能导入夹注'); return; }
  let saved = 0;
  try {
    const payload = JSON.parse(await file.text()) as { books?: { id: string; chapters: { chapterNo: number; commentary: string; annotations: { phrase: string; note: string; tone?: AnnotationTone }[] }[] }[] };
    const draftBook = payload.books?.find(item => matchesDraftBook(item.id, detail.value!));
    if (!draftBook) throw new Error('JSON 中没有与当前书目匹配的内容');
    const chapters = translation.value.chapters.filter(item => item.included);
    if (chapters.length !== draftBook.chapters.length) throw new Error(`章节数不匹配：当前 ${chapters.length} 章，注释文件 ${draftBook.chapters.length} 章`);
    const updates = chapters.map(chapter => {
      const source = draftBook.chapters.find(item => item.chapterNo === chapter.chapterNo);
      if (!source || typeof source.commentary !== 'string' || !Array.isArray(source.annotations)) throw new Error(`第 ${chapter.chapterNo} 章缺少解读或夹注`);
      let cursor = 0;
      const normalizedAnnotations: Annotation[] = source.annotations.map(annotation => ({ ...annotation, tone: annotation.tone || 'word' }));
      if (normalizedAnnotations.length > 1000) throw new Error(`第 ${chapter.chapterNo} 章夹注超过 1000 条`);
      for (const annotation of normalizedAnnotations) {
        if (!annotation.phrase || !annotation.note || !['word', 'clause', 'variant'].includes(annotation.tone)) throw new Error(`第 ${chapter.chapterNo} 章夹注字段不完整`);
        if (annotation.note.length > 1000) throw new Error(`第 ${chapter.chapterNo} 章有超过 1000 字的夹注`);
        const anchorText = translation.value!.targetLanguage === detail.value!.sourceLanguage ? chapter.sourceText : chapter.translationText;
        const start = anchorText.indexOf(annotation.phrase, cursor);
        if (start < 0) throw new Error(`第 ${chapter.chapterNo} 章夹注顺序或锚点与正文不一致：${annotation.phrase}`);
        cursor = start + annotation.phrase.length;
      }
      return { chapter, source, normalizedAnnotations };
    });
    await ElMessageBox.confirm(`将为 ${updates.length} 章写入章节解读和选定夹注，保留译文，并把章节校对状态重置为“待校对”。夹注可能只覆盖部分术语。继续吗？`, '导入阅读说明');
    busy.value = true;
    let current = translation.value;
    for (const { chapter, source, normalizedAnnotations } of updates) {
      const response = await api.put(`/books/${detail.value.id}/translations/${current.id}/chapters/${chapter.id}`, {
        expectedVersion: current.version,
        translationText: chapter.translationText,
        commentary: source.commentary,
        annotations: normalizedAnnotations,
      });
      current = response.data as Translation;
      translation.value = current;
      saved += 1;
    }
    await refreshTranslation();
    ElMessage.success(`已导入 ${saved} 章的解读与选定夹注；译文仍待人工校对，夹注不代表完整逐词覆盖。`);
  } catch (error) {
    if (saved) await refreshTranslation();
    const message = error instanceof Error ? error.message : errorText(error);
    ElMessage.error(saved ? `${message}（已更新 ${saved} 章，可核对后重新导入）` : message);
  } finally { busy.value = false; }
}
async function approveChapter(chapter: Chapter) {
  if (!detail.value || !translation.value) return;
  try { await ElMessageBox.confirm(`请确认第 ${chapter.chapterNo} 章已对照原文逐段校对，包含的夹注与解说也已核实。`, '确认章节校对'); }
  catch { return; }
  busy.value = true;
  try {
    await api.post(`/books/${detail.value.id}/translations/${translation.value.id}/chapters/${chapter.id}/review`, { expectedVersion: translation.value.version, approved: true });
    await refreshTranslation(); await load(); ElMessage.success('章节已通过人工校对');
  } catch (error) { ElMessage.error(errorText(error)); }
  finally { busy.value = false; }
}
async function publish() {
  if (!detail.value || !translation.value) return;
  try { await ElMessageBox.confirm('发布后，读者端将显示这一版译文与其许可、来源信息。请确认授权仍有效，且每章都已校对。', '确认上架', { type: 'warning', confirmButtonText: '确认上架' }); }
  catch { return; }
  busy.value = true;
  try { await api.post(`/books/${detail.value.id}/translations/${translation.value.id}/publish`, { expectedVersion: translation.value.version }); await refreshTranslation(); await openBook(detail.value); await load(); ElMessage.success('书籍版本已上架'); }
  catch (error) { ElMessage.error(errorText(error)); }
  finally { busy.value = false; }
}
async function unpublish() {
  if (!detail.value || !translation.value) return;
  try { await ElMessageBox.confirm('下架后，读者端将无法获取此版本；相应前端缓存会在下次目录刷新时失效。', '确认下架', { type: 'warning' }); }
  catch { return; }
  busy.value = true;
  try { await api.post(`/books/${detail.value.id}/translations/${translation.value.id}/unpublish`, { expectedVersion: translation.value.version }); await refreshTranslation(); await openBook(detail.value); await load(); ElMessage.success('版本已下架'); }
  catch (error) { ElMessage.error(errorText(error)); }
  finally { busy.value = false; }
}
async function revokeRights() {
  if (!detail.value) return;
  try { await ElMessageBox.confirm('撤回授权会立即停止全部语言版本公开展示，并标记为已撤回。', '撤回书籍授权', { type: 'warning', confirmButtonText: '确认撤回' }); }
  catch { return; }
  busy.value = true;
  try { await api.post(`/books/${detail.value.id}/revoke`, { expectedVersion: detail.value.version }); await openBook(detail.value); await load(); ElMessage.success('授权已撤回'); }
  catch (error) { ElMessage.error(errorText(error)); }
  finally { busy.value = false; }
}
async function downloadSourceTranslation() {
  if (!detail.value || !translation.value?.uploadedFile) return;
  busy.value = true;
  try {
    const response = await api.get(`/books/${detail.value.id}/translations/${translation.value.id}/asset`, { responseType: 'blob' });
    const url = URL.createObjectURL(response.data); const anchor = document.createElement('a'); anchor.href = url; anchor.download = translation.value.uploadedFile.filename; anchor.click(); URL.revokeObjectURL(url);
  } catch (error) { ElMessage.error(errorText(error)); }
  finally { busy.value = false; }
}

onMounted(() => { void load(); });
</script>

<template>
  <section class="books-page">
    <div class="page-heading"><div><div class="eyebrow">BOOKS &amp; EDITIONS</div><h1>书籍管理</h1><p class="muted">书籍内容独立管理。只有授权核实、来源确认、逐章校对通过的版本才会进入读者书架。</p></div></div>
    <div class="book-actions"><el-button type="primary" :disabled="busy" @click="newBook">导入书籍</el-button><el-button :disabled="busy" @click="load">刷新</el-button></div>
    <el-alert title="导入文件会留在书籍后台，不会进入聊天参考资料。上传个人副本、标明出处或使用本地 AI 翻译本身都不代表取得公开展示授权。" type="info" :closable="false" />

    <div class="book-layout">
      <aside class="book-list" v-loading="busy">
        <el-empty v-if="!books.length" description="还没有导入书籍" />
        <button v-for="book in books" :key="book.id" class="book-list-item" :class="{ selected: activeBook?.id === book.id }" @click="openBook(book)">
          <strong>{{ book.title }}</strong><span>{{ book.sourceVersion }} · {{ languageLabel(book.sourceLanguage) }}</span>
          <div class="book-tags"><el-tag size="small" :type="book.authorizationVerified ? 'success' : 'warning'">{{ book.authorizationVerified ? '授权已核实' : '待补授权' }}</el-tag><el-tag size="small" :type="statusType(book.latestTranslationStatus || book.state)">{{ statusLabels[book.latestTranslationStatus || book.state] || book.latestTranslationStatus || book.state }}</el-tag></div>
        </button>
      </aside>

      <main v-if="detail" class="book-detail" v-loading="busy">
        <div class="detail-heading"><div><div class="eyebrow">{{ detail.author || detail.publisher || '未署名' }} · {{ detail.sourceVersion }}</div><h2>{{ detail.title }}</h2><p class="muted">{{ detail.summary || '尚未填写简介。' }}</p></div><el-tag :type="statusType(detail.state)">{{ statusLabels[detail.state] || detail.state }}</el-tag></div>
        <div class="book-actions"><el-button @click="editRights">书目信息与授权</el-button><el-button :disabled="busy" @click="revokeRights">撤回授权</el-button></div>
        <el-alert v-if="!canTranslate" :title="canImportLocalDraft ? '当前仅允许将本地译稿归档为管理员待审版本；服务器翻译与公开上架须先核实权利方、许可范围和使用授权。' : '请先核对解析后的来源章节；来源文本确认不代表版权或使用许可已核实。权利与许可未确认前，仅可保留后台草稿。'" type="warning" :closable="false" />
        <div class="source-panel">
          <div class="section-heading"><div><h3>来源版本 · {{ detail.sourceVersionNo || 1 }}</h3><p class="muted">{{ detail.sourceUrl || '尚未填写来源链接' }} · {{ detail.sourceChapters.length }} 个解析章节</p></div><el-tag :type="detail.sourceReviewed ? 'success' : 'warning'">{{ detail.sourceReviewed ? '来源已确认' : '待确认来源' }}</el-tag></div>
          <div class="book-actions"><el-button size="small" @click="sourceEditor = !sourceEditor">{{ sourceEditor ? '收起编辑' : '检查 / 修正章节' }}</el-button><el-button v-if="sourceEditor" size="small" type="primary" :disabled="busy" @click="saveSourceChapters">保存解析修改</el-button><el-button size="small" type="success" :disabled="busy || detail.sourceReviewed || !detail.sourceChapters.length" @click="reviewSource">确认文本一致</el-button></div>
          <div v-for="chapter in sourceChapters" :key="chapter.chapterNo" class="source-chapter">
            <el-input v-if="sourceEditor" v-model="chapter.title" :placeholder="`第 ${chapter.chapterNo} 章标题`" />
            <h4 v-else>第 {{ chapter.chapterNo }} 章 · {{ chapter.title }}</h4>
            <el-input v-if="sourceEditor" v-model="chapter.sourceText" type="textarea" :autosize="{ minRows: 4, maxRows: 16 }" />
            <p v-else class="source-preview">{{ chapter.sourceText }}</p>
          </div>
        </div>

        <div class="section-heading translation-heading"><div><h3>译稿与发布版本</h3><p class="muted">本地译稿上传后不会在服务器再次调用模型；未核实授权时只会进入后台待审状态。</p></div><div class="book-actions"><el-button :disabled="busy || !canImportLocalDraft" @click="startTranslation">上传本地译稿</el-button><el-button type="primary" :disabled="busy || !canTranslate" @click="startTranslation">服务器翻译</el-button></div></div>
        <el-empty v-if="!detail.translations.length" description="确认来源与授权后，可以开始第一版翻译" />
        <div v-for="item in detail.translations" :key="item.id" class="translation-row">
          <div><strong>{{ languageLabel(item.targetLanguage) }} · v{{ item.revisionNo }}</strong><span class="muted">来源 v{{ item.sourceVersionNo }} · {{ item.origin === 'LOCAL_UPLOAD' ? `本地上传${item.modelNote ? ` · ${item.modelNote}` : ''}` : '服务器模型' }}</span></div>
          <el-tag :type="statusType(item.status)">{{ statusLabels[item.status] || item.status }}</el-tag>
          <el-button size="small" @click="openTranslation(item.id)">打开译稿</el-button>
        </div>

        <div v-if="translation" class="translation-workspace">
          <div class="section-heading"><div><h3>{{ languageLabel(translation.targetLanguage) }} · v{{ translation.revisionNo }}</h3><p class="muted">{{ translation.origin === 'LOCAL_UPLOAD' ? `本地模型 ${translation.modelNote || '未记录'}` : '服务器模型生成' }} · 质检硬性问题 {{ translation.qaHardIssues }}</p></div><el-tag :type="statusType(translation.status)">{{ statusLabels[translation.status] || translation.status }}</el-tag></div>
          <p v-if="translation.translatorNote" class="muted">{{ translation.translatorNote }}</p>
          <p v-if="translation.uploadedFile" class="uploaded-asset">归档译稿：{{ translation.uploadedFile.filename }} · SHA-256 {{ translation.uploadedFile.sha256 }} <el-button link type="primary" @click="downloadSourceTranslation">下载原始译稿</el-button></p>
          <div class="book-actions"><el-button v-if="editable" size="small" :disabled="busy" @click="annotationsFileInput?.click()">导入解读与夹注 JSON</el-button><el-button v-if="translation.status === 'FAILED'" size="small" type="warning" :disabled="busy" @click="retryTranslation">重试失败章节</el-button><el-button size="small" :disabled="busy || !['NEEDS_REVIEW','QA_FAILED','READY'].includes(translation.status)" @click="runQa">运行章节质检</el-button><el-button size="small" :disabled="busy" @click="previewDialog = true">读者端预览</el-button><el-button v-if="translation.status === 'READY'" size="small" type="primary" :disabled="busy" @click="publish">确认上架</el-button><el-button v-if="translation.status === 'PUBLISHED'" size="small" type="warning" :disabled="busy" @click="unpublish">下架版本</el-button><el-button size="small" :disabled="busy" @click="refreshTranslation">刷新状态</el-button></div>
          <p v-if="editable" class="form-hint">批量导入仅写入章节解读和选词夹注，并按章节顺序核对锚点；不要求逐词覆盖。导入后仍需人工逐章审校。</p>
          <el-alert v-if="translation.status === 'QA_FAILED'" title="存在未通过的结构或完整性检查。请按章节修正后重新运行质检。" type="error" :closable="false" />
          <el-alert v-else-if="translation.status === 'NEEDS_REVIEW'" title="自动质检只提示结构问题，不判断经典含义。发布前仍须逐章对照原文并确认校对。" type="warning" :closable="false" />
          <div v-for="chapter in translation.chapters.filter(item => item.included)" :key="chapter.id" class="review-chapter">
            <div class="section-heading"><div><h4>第 {{ chapter.chapterNo }} 章 · {{ chapter.title }}</h4><p class="muted">{{ chapter.translationState === 'COMPLETE' ? '译文已保存' : chapter.failureReason || chapter.translationState }} · {{ chapter.reviewState === 'APPROVED' ? '已人工校对' : '待人工校对' }}</p></div><div class="book-actions"><el-button v-if="editable" size="small" @click="editChapter(chapter)">编辑译文与注释</el-button><el-button v-if="editable && translation.qaReport" size="small" type="success" :disabled="busy || chapter.reviewState === 'APPROVED' || translation.qaHardIssues > 0" @click="approveChapter(chapter)">确认本章校对</el-button></div></div>
            <div class="chapter-compare"><div><div class="eyebrow">原文</div><p>{{ chapter.sourceText }}</p></div><div><div class="eyebrow">译文</div><p>{{ chapter.translationText || '尚无译文' }}</p><p v-if="chapter.commentary" class="commentary-preview">{{ chapter.commentary }}</p></div></div>
            <el-alert v-for="issue in chapter.qaReport || []" :key="issue.code + issue.message" :title="issue.message" :type="issue.severity === 'HARD' ? 'error' : 'warning'" :closable="false" class="qa-alert" />
          </div>
          <div v-if="translation.status === 'PUBLISHED'" class="published-note">读者端可按书目、译文版本和语言获取此发布快照。下架或授权撤回后，目录会停止提供此版本。</div>
        </div>
      </main>
      <main v-else class="book-detail empty-detail"><el-empty description="选择一本书查看来源、授权与译稿流程" /></main>
    </div>

    <input ref="sourceFileInput" type="file" accept=".epub,.docx,.txt,.md,.pdf" hidden @change="chooseSource" />
    <input ref="translationFileInput" type="file" accept=".epub,.docx,.txt,.md,.pdf" hidden @change="chooseTranslation" />
    <input ref="annotationsFileInput" type="file" accept=".json,application/json" hidden @change="importAnnotations" />
    <el-dialog v-model="uploadDialog" title="导入书籍来源" width="680px" :close-on-click-modal="false">
      <el-form label-position="top" class="form-grid">
        <el-form-item label="来源文件"><div class="file-picker"><el-button @click="sourceFileInput?.click()">选择 EPUB / DOCX / TXT / Markdown / PDF</el-button><span>{{ sourceFile?.name || '仅支持可提取文字的 PDF' }}</span></div></el-form-item>
        <el-form-item label="书名"><el-input v-model="bookForm.title" maxlength="200" /></el-form-item><el-form-item label="作者"><el-input v-model="bookForm.author" maxlength="200" /></el-form-item><el-form-item label="发布方"><el-input v-model="bookForm.publisher" maxlength="200" /></el-form-item>
        <el-form-item label="原文语言"><el-select v-model="bookForm.sourceLanguage"><el-option label="英语" value="en-US"/><el-option label="简体中文" value="zh-CN"/></el-select></el-form-item><el-form-item label="版本 / 版次"><el-input v-model="bookForm.sourceVersion" maxlength="128" /></el-form-item>
        <el-form-item label="来源链接"><el-input v-model="bookForm.sourceUrl" maxlength="1024" /></el-form-item><el-form-item label="书架分类"><el-select v-model="bookForm.category"><el-option label="经典" value="classics"/><el-option label="入门" value="guides"/><el-option label="生活中的佛法" value="stories"/></el-select></el-form-item><el-form-item label="简介"><el-input v-model="bookForm.summary" type="textarea" maxlength="2000" /></el-form-item>
        <el-divider content-position="left">授权信息</el-divider>
        <el-form-item label="权利方（选填）"><el-input v-model="bookForm.rightsHolder" /></el-form-item><el-form-item label="许可名称（选填）"><el-input v-model="bookForm.licenseName" /></el-form-item><el-form-item label="许可依据 / 证据（选填）"><el-input v-model="bookForm.licenseEvidence" type="textarea" /></el-form-item><el-form-item label="适用地区（选填）"><el-input v-model="bookForm.licenseTerritory" /></el-form-item><el-form-item label="期限（选填）"><el-input v-model="bookForm.licenseTerm" placeholder="例如：永久 / 截止日期" /></el-form-item><el-form-item label="失效日期（选填）"><el-date-picker v-model="bookForm.licenseExpiresOn" value-format="YYYY-MM-DD" /></el-form-item>
        <div class="rights-checks"><el-checkbox v-model="bookForm.permissionDisplay">产品内展示</el-checkbox><el-checkbox v-model="bookForm.permissionTranslate">翻译</el-checkbox><el-checkbox v-model="bookForm.permissionAdapt">改编 / 解说</el-checkbox><el-checkbox v-model="bookForm.permissionAi">AI 辅助处理</el-checkbox><el-checkbox v-model="bookForm.authorizationVerified">我已核实以上授权</el-checkbox></div>
        <div class="form-hint" style="grid-column:1/-1" role="status">{{ missingRightsFields(bookForm).length ? '确认授权前，请补齐：' + missingRightsFields(bookForm).join('、') : '权利方、许可名称、许可依据、适用地区、期限和失效日期均为选填；确认授权需保留来源链接并勾选产品内展示权限。' }}</div>
      </el-form>
      <template #footer><el-button @click="uploadDialog = false">取消</el-button><el-button type="primary" :loading="busy" @click="importBook">导入为草稿</el-button></template>
    </el-dialog>
    <el-dialog v-model="rightsOpen" title="书目信息与授权" width="680px" :close-on-click-modal="false">
      <el-form label-position="top" class="form-grid">
        <el-form-item label="书名"><el-input v-model="rightsForm.title" /></el-form-item><el-form-item label="作者"><el-input v-model="rightsForm.author" /></el-form-item><el-form-item label="发布方"><el-input v-model="rightsForm.publisher" /></el-form-item><el-form-item label="版本 / 版次"><el-input v-model="rightsForm.sourceVersion" /></el-form-item><el-form-item label="来源链接"><el-input v-model="rightsForm.sourceUrl" /></el-form-item><el-form-item label="分类"><el-select v-model="rightsForm.category"><el-option label="经典" value="classics"/><el-option label="入门" value="guides"/><el-option label="生活中的佛法" value="stories"/></el-select></el-form-item><el-form-item label="简介"><el-input v-model="rightsForm.summary" type="textarea" /></el-form-item><el-form-item label="权利方（选填）"><el-input v-model="rightsForm.rightsHolder" /></el-form-item><el-form-item label="许可名称（选填）"><el-input v-model="rightsForm.licenseName" /></el-form-item><el-form-item label="许可依据 / 证据（选填）"><el-input v-model="rightsForm.licenseEvidence" type="textarea" /></el-form-item><el-form-item label="适用地区（选填）"><el-input v-model="rightsForm.licenseTerritory" /></el-form-item><el-form-item label="期限（选填）"><el-input v-model="rightsForm.licenseTerm" /></el-form-item><el-form-item label="失效日期（选填）"><el-date-picker v-model="rightsForm.licenseExpiresOn" value-format="YYYY-MM-DD" /></el-form-item>
        <div class="rights-checks"><el-checkbox v-model="rightsForm.permissionDisplay">产品内展示</el-checkbox><el-checkbox v-model="rightsForm.permissionTranslate">翻译</el-checkbox><el-checkbox v-model="rightsForm.permissionAdapt">改编 / 解说</el-checkbox><el-checkbox v-model="rightsForm.permissionAi">AI 辅助处理</el-checkbox><el-checkbox v-model="rightsForm.authorizationVerified">我已核实以上授权</el-checkbox></div>
        <div class="form-hint" style="grid-column:1/-1" role="status">{{ missingRightsFields(rightsForm).length ? '确认授权前，请补齐：' + missingRightsFields(rightsForm).join('、') : '权利方、许可名称、许可依据、适用地区、期限和失效日期均为选填；确认授权需保留来源链接并勾选产品内展示权限。' }}</div>
      </el-form>
      <template #footer><el-button @click="rightsOpen = false">取消</el-button><el-button type="primary" :loading="busy" @click="saveRights">保存</el-button></template>
    </el-dialog>
    <el-dialog v-model="translationDialog" title="创建译稿版本" width="620px" :close-on-click-modal="false">
      <el-form label-position="top"><el-form-item label="目标语言"><el-select v-model="translationForm.targetLanguage"><el-option v-for="language in ['en-US','zh-CN'].filter(item => item !== detail?.sourceLanguage || item === 'zh-CN')" :key="language" :label="languageLabel(language)" :value="language" /></el-select><div v-if="translationForm.targetLanguage === detail?.sourceLanguage" class="form-hint">汉文古籍的现代汉语译注请通过本地译稿上传；原文与白话分别保存。</div></el-form-item>
        <el-form-item label="章节范围（可选）"><el-input v-model="chapterNumbers" placeholder="留空表示全书；节选可输入 1, 2, 3" /><div class="form-hint">{{ detail?.sourceChapters.length }} 个来源章节。节选译稿必须按这里的顺序排列，并填写节选名称。</div></el-form-item><el-form-item label="节选名称"><el-input v-model="translationForm.excerptLabel" maxlength="200" placeholder="完整翻译可留空" /></el-form-item>
        <el-divider content-position="left">模型译稿上传（不会调用服务器模型）</el-divider><el-form-item label="译稿文件"><div class="file-picker"><el-button @click="translationFileInput?.click()">选择 EPUB / DOCX / TXT / Markdown / PDF</el-button><span>{{ translationFile?.name || '需保留章节标题，章节数与所选范围一致' }}</span></div></el-form-item><el-form-item label="模型 / 译者说明"><el-input v-model="translationForm.modelNote" placeholder="例如：Codex（当前会话模型）" /></el-form-item><el-form-item label="译稿说明"><el-input v-model="translationForm.translatorNote" type="textarea" placeholder="可记录版本、术语约定或人工编辑范围" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="translationDialog = false">取消</el-button><el-button :disabled="!canTranslate || busy || translationForm.targetLanguage === detail?.sourceLanguage" @click="createServerTranslation">使用服务器模型</el-button><el-button type="primary" :loading="busy" @click="importLocalTranslation">归档本地译稿</el-button></template>
    </el-dialog>
    <el-dialog v-model="chapterDialog" :title="selectedChapter ? `校对第 ${selectedChapter.chapterNo} 章` : '章节校对'" width="920px" :close-on-click-modal="false">
      <div v-if="selectedChapter" class="chapter-compare chapter-dialog-compare"><div><div class="eyebrow">来源原文</div><p>{{ selectedChapter.sourceText }}</p></div><div class="chapter-edit-form"><el-form label-position="top"><el-form-item label="译文"><el-input v-model="chapterForm.translationText" type="textarea" :autosize="{ minRows: 12, maxRows: 24 }" /></el-form-item><el-form-item label="此间白话 / 章节解读"><el-input v-model="chapterForm.commentary" type="textarea" :autosize="{ minRows: 3, maxRows: 8 }" /></el-form-item></el-form></div></div>
      <div class="annotations-edit"><div class="section-heading"><div><h4>逐段夹注</h4><p class="form-hint">青绿释词、朱红释句、赭色标出版本差异。</p></div><el-button size="small" @click="annotations.push({ phrase: '', note: '', tone: 'word' })">添加夹注</el-button></div><div v-for="(item, index) in annotations" :key="index" class="annotation-row"><el-input v-model="item.phrase" placeholder="译文锚点"/><el-input v-model="item.note" placeholder="对应解释"/><el-select v-model="item.tone" aria-label="夹注类型"><el-option label="释词" value="word"/><el-option label="释句" value="clause"/><el-option label="异文" value="variant"/></el-select><el-button text type="danger" @click="annotations.splice(index, 1)">删除</el-button></div></div>
      <template #footer><el-button @click="chapterDialog = false">取消</el-button><el-button type="primary" :loading="busy" @click="saveChapter">保存修改并重新质检</el-button></template>
    </el-dialog>
    <el-dialog v-model="previewDialog" title="读者端预览" width="760px"><div v-if="translation" class="reader-preview"><div class="eyebrow">{{ languageLabel(translation.targetLanguage) }} · {{ detail?.title }}</div><h2>{{ detail?.title }}</h2><p class="muted">{{ detail?.author }} · {{ detail?.sourceVersion }} · v{{ translation.revisionNo }}</p><el-alert v-if="translation.isExcerpt" :title="translation.excerptLabel || '选读'" type="info" :closable="false"/><article v-for="chapter in translation.chapters.filter(item => item.included)" :key="chapter.id"><h3>{{ chapter.title }}</h3><h4>原文</h4><p v-for="(paragraph,index) in chapter.sourceText.split(/\n\s*\n/).filter(Boolean)" :key="`original-${index}`">{{ paragraph }}</p><h4>{{ translation.targetLanguage === detail?.sourceLanguage ? '白话译文' : '译文' }}</h4><p v-for="(paragraph,index) in chapter.translationText.split(/\n\s*\n/).filter(Boolean)" :key="index">{{ paragraph }}</p><el-alert v-if="chapter.commentary" :title="chapter.commentary" type="info" :closable="false"/><ul v-if="chapter.annotations?.length"><li v-for="item in chapter.annotations" :key="item.phrase"><strong>{{ item.phrase }}</strong>：{{ item.note }}</li></ul></article></div></el-dialog>
  </section>
</template>

<style scoped>
.books-page{display:grid;gap:18px}.book-actions{display:flex;align-items:center;gap:8px;flex-wrap:wrap}.book-layout{display:grid;grid-template-columns:minmax(220px,280px) minmax(0,1fr);gap:18px;align-items:start}.book-list,.book-detail{background:#fffefa;border:1px solid #e6e5dc;border-radius:18px;padding:18px;min-width:0}.book-list{position:sticky;top:12px;max-height:calc(100vh - 160px);overflow:auto}.book-list-item{display:grid;width:100%;text-align:left;gap:6px;padding:13px 12px;background:transparent;border:0;border-bottom:1px solid #eeede7;color:#30392f;cursor:pointer}.book-list-item.selected{background:#f0f2eb;border-radius:12px}.book-list-item span,.muted{color:#758074;font-size:13px}.book-tags{display:flex;gap:6px;flex-wrap:wrap}.book-detail{display:grid;gap:18px}.empty-detail{min-height:300px;place-items:center}.detail-heading,.section-heading{display:flex;justify-content:space-between;align-items:flex-start;gap:14px}.detail-heading h2,.section-heading h3{margin:4px 0}.source-panel,.translation-workspace{border-top:1px solid #e7e6df;padding-top:16px;display:grid;gap:14px}.source-chapter,.review-chapter{border:1px solid #e9e8e2;border-radius:14px;padding:14px;display:grid;gap:9px}.source-chapter h4,.review-chapter h4{margin:0}.source-preview,.chapter-compare p{white-space:pre-wrap;line-height:1.72;margin:8px 0;color:#3b433b}.chapter-compare{display:grid;grid-template-columns:1fr 1fr;gap:14px}.chapter-compare>div{min-width:0;background:#f7f7f2;border-radius:12px;padding:12px;max-height:420px;overflow:auto;white-space:pre-wrap}.chapter-compare .eyebrow{margin-bottom:8px}.commentary-preview{border-left:3px solid #a9b59c;padding-left:10px;color:#66705f!important}.translation-row{display:grid;grid-template-columns:minmax(0,1fr) auto auto;align-items:center;gap:12px;border:1px solid #e9e8e2;border-radius:12px;padding:12px}.translation-row>div{display:grid;gap:4px}.uploaded-asset{font-size:13px;color:#687465}.qa-alert{margin-top:6px}.published-note,.form-hint{font-size:13px;color:#778073}.form-grid{display:grid;grid-template-columns:1fr 1fr;column-gap:16px}.form-grid .el-form-item:first-child,.form-grid .el-divider,.rights-checks{grid-column:1/-1}.rights-checks{display:flex;gap:18px;flex-wrap:wrap}.file-picker{display:flex;align-items:center;gap:12px;flex-wrap:wrap}.file-picker span{color:#768072;font-size:13px}.annotation-row{display:grid;grid-template-columns:1fr 2fr auto;gap:8px;margin:8px 0}.annotations-edit{margin-top:16px}.reader-preview{max-width:620px;margin:auto;line-height:1.8}.reader-preview article{padding:18px 0;border-top:1px solid #e9e8e2}.reader-preview article p{white-space:pre-wrap}.reader-preview h2,.reader-preview h3{font-family:serif;font-weight:500}
@media(max-width:900px){.book-layout{grid-template-columns:1fr}.book-list{position:static;max-height:260px}.chapter-compare{grid-template-columns:1fr}.translation-row{grid-template-columns:1fr auto}.translation-row>div{grid-column:1/-1}.form-grid{grid-template-columns:1fr}}
.annotation-row{grid-template-columns:minmax(120px,1fr) minmax(200px,2fr) 120px auto}
@media(max-width:700px){.annotation-row{grid-template-columns:1fr}.annotation-row>*{width:100%}}
</style>

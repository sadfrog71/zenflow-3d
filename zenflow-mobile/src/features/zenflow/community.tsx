import { Alert } from '@/platform/zenflow/alert';
import { useEffect, useEffectEvent, useMemo, useRef, useState } from 'react';
import { Image, Platform, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import type { ZenflowSession } from '@/core/zenflow/session';
import { CoreApi } from '@/core/zenflow/core';
import { CommunityApi, type CommunityCategory, type CommunityImage, type CommunityPost } from '@/core/zenflow/community';
import { VoiceInput } from './voice-input';
type Props = {
  session: ZenflowSession;
  english: boolean;
  active: boolean;
  onFocusChange?: (focused: boolean) => void;
};
function CommunityButton({
  title,
  onPress,
  primary = false,
  disabled = false
}: {
  title: string;
  onPress: () => void;
  primary?: boolean;
  disabled?: boolean;
}) {
  return <Pressable accessibilityRole="button" onPress={onPress} disabled={disabled} style={[s.button, primary && s.primary, disabled && {
    opacity: .45
  }]}><Text style={[s.buttonText, primary && {
      color: '#fffef5'
    }]}>{title}</Text></Pressable>;
}
const categories = ['ALL', 'SHORT', 'ESSAY', 'POEM'] as const;
export function CommunitySpace({
  session,
  english,
  active,
  onFocusChange
}: Props) {
  const api = useMemo(() => new CommunityApi(session), [session]);
  const voiceApi = useMemo(() => new CoreApi(session), [session]);
  const [rows, setRows] = useState<CommunityPost[]>([]),
    [mine, setMine] = useState(false),
    [filter, setFilter] = useState('ALL'),
    [cursor, setCursor] = useState<string>(),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(''),
    [selected, setSelected] = useState<CommunityPost>(),
    [editing, setEditing] = useState(false),
    [title, setTitle] = useState(''),
    [text, setText] = useState(''),
    [category, setCategory] = useState<CommunityCategory>('ESSAY'),
    [images, setImages] = useState<CommunityImage[]>([]),
    [fullImages, setFullImages] = useState<string[]>([]);
  const request = useRef(0),
    alive = useRef(true),
    working = useRef(false),
    imageDraft = useRef(images);
  useEffect(() => {
    imageDraft.current = images;
  }, [images]);
  const label = (key: string) => ({
    ALL: english ? 'All' : '全部',
    SHORT: english ? 'Short words' : '短句',
    ESSAY: english ? 'Essays' : '随笔',
    POEM: english ? 'Poems' : '诗',
    PENDING: english ? 'Awaiting review' : '待审核',
    APPROVED: english ? 'Public' : '已公开',
    REJECTED: english ? 'Not approved' : '未通过审核',
    WITHDRAWN: english ? 'Withdrawn' : '已撤回',
    REMOVED: english ? 'Removed' : '已下架'
  })[key] || key;
  const failed = (e: unknown) => {
    const code = e instanceof Error ? e.message : '';
    setError(code === 'COMMUNITY_IMAGE_INVALID' ? english ? 'Choose a JPG or PNG image under 5 MB.' : '请选择 5 MB 以内的 JPG 或 PNG 图片。' : english ? 'Could not complete this action. Please try again.' : '操作未完成，请重试。');
  };
  function load(more = false) {
    const ticket = ++request.current;
    return api.posts(mine, filter, more ? cursor : undefined).then(page => {
      if (!alive.current || ticket !== request.current) return;
      setError('');
      setRows(previous => more ? [...previous, ...page.items] : page.items);
      setCursor(page.hasMore ? page.nextCursor || undefined : undefined);
    }).catch(e => {
      if (alive.current && ticket === request.current) failed(e);
    }).finally(() => {
      if (alive.current && ticket === request.current) setBusy(false);
    });
  }
  const refresh = useEffectEvent(() => {
    if (active && !editing && !selected) void load();
  });
  useEffect(() => refresh(), [active, mine, filter]);
  useEffect(() => {
    onFocusChange?.(active && editing);
    return () => onFocusChange?.(false);
  }, [active, editing, onFocusChange]);
  useEffect(() => {
    alive.current = true;
    return () => {
      alive.current = false;
      request.current++;
      for (const image of imageDraft.current) void api.discard(image.id).catch(() => {});
    };
  }, [api]);
  function cancel() {
    for (const image of images) void api.discard(image.id).catch(() => {});
    setImages([]);
    setEditing(false);
    setTitle('');
    setText('');
    setError('');
  }
  async function chooseImage() {
    if (working.current || images.length >= 6) return;
    working.current = true;
    setBusy(true);
    setError('');
    try {
      const picker = require('expo-image-picker');
      const result = await picker.launchImageLibraryAsync({
        mediaTypes: ['images'],
        quality: .85,
        base64: true,
        allowsMultipleSelection: false
      });
      if (result.canceled) return;
      const asset = result.assets[0];
      if (!asset.base64) throw new Error('COMMUNITY_IMAGE_INVALID');
      const uploaded = await api.upload(asset.base64);
      if (alive.current) setImages(previous => [...previous, uploaded]);else void api.discard(uploaded.id).catch(() => {});
    } catch (e) {
      if (alive.current) failed(e);
    } finally {
      working.current = false;
      if (alive.current) setBusy(false);
    }
  }
  async function publish() {
    if (working.current || !title.trim() || !text.trim()) return;
    working.current = true;
    setBusy(true);
    setError('');
    try {
      const post = await api.submit(title.trim(), text.trim(), category, images);
      if (!alive.current) return;
      setImages([]);
      setTitle('');
      setText('');
      setEditing(false);
      setMine(true);
      setSelected(post);
      Alert.alert(english ? 'Page submitted' : '投稿已送出', english ? 'Your page stays private until an administrator approves it.' : '作品审核通过后才会出现在社区。');
    } catch (e) {
      if (alive.current) failed(e);
    } finally {
      working.current = false;
      if (alive.current) setBusy(false);
    }
  }
  async function open(post: CommunityPost) {
    setError('');
    setFullImages([]);
    setBusy(true);
    try {
      const current = await api.post(post.id);
      if (alive.current) setSelected(current);
    } catch (e) {
      if (alive.current) failed(e);
    } finally {
      if (alive.current) setBusy(false);
    }
  }
  async function readImages() {
    if (!selected) return;
    setBusy(true);
    try {
      const values = await Promise.all(selected.images.map(image => api.image(image.id)));
      if (alive.current) setFullImages(values.map(value => value.uri));
    } catch (e) {
      if (alive.current) failed(e);
    } finally {
      if (alive.current) setBusy(false);
    }
  }
  function withdraw() {
    if (!selected) return;
    const post = selected;
    Alert.alert(english ? 'Withdraw this page?' : '撤回这篇作品？', english ? 'It will no longer be visible in the community.' : '撤回后社区将不再显示这篇作品及图片。', [{
      text: english ? 'Cancel' : '取消',
      style: 'cancel'
    }, {
      text: english ? 'Withdraw' : '撤回',
      style: 'destructive',
      onPress: async () => {
        setBusy(true);
        try {
          const result = await api.withdraw(post);
          if (alive.current) {
            setSelected(result);
            void load();
          }
        } catch (e) {
          if (alive.current) failed(e);
        } finally {
          if (alive.current) setBusy(false);
        }
      }
    }]);
  }
  if (editing) return <View style={{
    flex: 1
  }}><View style={s.editorTop}>{<CommunityButton title={english ? 'Back' : '返回'} onPress={cancel} primary={false} disabled={busy} />}<Text style={s.small}>{english ? 'A PAGE TO SHARE' : '写下一页 · 留给此间'}</Text>{<CommunityButton title={english ? 'Submit' : '投稿'} onPress={() => void publish()} primary={true} disabled={busy || !title.trim() || !text.trim()} />}</View><ScrollView keyboardShouldPersistTaps="handled" contentContainerStyle={s.editorBody}><View style={s.paper}><View style={s.tape} /><Text style={s.paperDate}>{new Date().toLocaleDateString(english ? 'en-US' : 'zh-CN', {
            month: 'long',
            day: 'numeric'
          })}</Text><TextInput accessibilityLabel={english ? 'Title' : '作品标题'} value={title} onChangeText={setTitle} maxLength={120} placeholder={english ? 'Give this page a title…' : '给这一页取个名字…'} placeholderTextColor="#a0a78f" style={s.titleInput} /><VoiceInput correctVoiceSentence={(request, signal) => voiceApi.correctVoiceSentence(request, signal)} en={english} target={english ? 'Title' : '作品标题'} maxLength={120} label={english ? 'Speak a title' : '口述标题'} disabled={busy} onUse={value => setTitle(previous => (previous + value).slice(0, 120))} /><TextInput accessibilityLabel={english ? 'Page text' : '作品正文'} multiline value={text} onChangeText={setText} maxLength={50000} placeholder={english ? 'A thought, a memory, a little light.' : '一个念头，一段记忆，一点光。'} placeholderTextColor="#a0a78f" style={s.textInput} /><VoiceInput correctVoiceSentence={(request, signal) => voiceApi.correctVoiceSentence(request, signal)} en={english} target={english ? 'Writing' : '作品正文'} maxLength={50000} label={english ? 'Speak your page' : '说出这一页'} disabled={busy} onUse={value => setText(previous => (previous + (previous ? '\n' : '') + value).slice(0, 50000))} /></View><View style={s.filters}>{(['ESSAY', 'SHORT', 'POEM'] as const).map(key => <Pressable key={key} accessibilityRole="button" onPress={() => setCategory(key)} style={[s.chip, category === key && s.selectedChip]}><Text style={s.chipText}>{label(key)}</Text></Pressable>)}</View><View style={s.imageGrid}>{images.map(image => <View key={image.id}><Image source={{
            uri: image.thumbnail
          }} style={s.draftImage} />{<CommunityButton title={english ? 'Remove' : '移除'} onPress={() => {
            void api.discard(image.id).catch(failed);
            setImages(previous => previous.filter(value => value.id !== image.id));
          }} primary={false} disabled={busy} />}</View>)}</View>{<CommunityButton title={busy ? '…' : english ? 'Add a picture' : '添加图片'} onPress={() => void chooseImage()} primary={false} disabled={busy || images.length >= 6} />}<Text style={s.note}>{english ? 'Up to 6 JPG/PNG pictures, 5 MB each. Submitting sends this page for review; it is not published immediately. Your personal notes remain private.' : '最多 6 张 JPG/PNG 图片，每张 5 MB。投稿后进入审核，通过后公开；你的个人记录仍然私密。'}</Text>{!!error && <Text style={s.error}>{error}</Text>}</ScrollView></View>;
  if (selected) return <ScrollView contentContainerStyle={s.body} keyboardShouldPersistTaps="handled">{<CommunityButton title={english ? 'Back to pages' : '返回作品列表'} onPress={() => {
      setSelected(undefined);
      void load();
    }} />}<Text style={s.eyebrow}>{label(selected.category)} · {selected.author}</Text><Text style={s.heading}>{selected.title}</Text>{selected.isOwner && <Text style={s.small}>{label(selected.state)}</Text>}<View style={s.paper}><Text selectable style={s.detailText}>{selected.text}</Text></View><View style={s.detailImages}>{selected.images.map((image, index) => <Image key={image.id} source={{
        uri: fullImages[index] || image.thumbnail
      }} style={[s.fullImage, {
        aspectRatio: (image.width || 1) / (image.height || 1)
      }]} resizeMode="contain" />)}</View>{!!selected.images.length && !fullImages.length && <CommunityButton title={english ? 'Load full pictures' : '查看完整图片'} onPress={() => void readImages()} primary={false} disabled={busy} />}{!!selected.reviewNote && <Text style={s.note}>{english ? 'Review note: ' : '审核意见：'}{selected.reviewNote}</Text>}{selected.isOwner && ['PENDING', 'APPROVED', 'REJECTED'].includes(selected.state) && <CommunityButton title={english ? 'Withdraw page' : '撤回作品'} onPress={withdraw} primary={false} disabled={busy} />}{!!error && <Text style={s.error}>{error}</Text>}</ScrollView>;
  const featured = !mine && filter === 'ALL' ? rows.find(post => post.featured) : undefined;
  return <ScrollView contentContainerStyle={s.body} keyboardShouldPersistTaps="handled"><Text style={s.eyebrow}>{english ? 'WORDS WE SHARE' : '此间 · 创作社区'}</Text><Text style={s.heading}>{english ? 'A little of us,\non a page.' : '在文字里，\n遇见彼此。'}</Text><Text style={s.subheading}>{english ? 'Small stories, passing thoughts, a little light from someone else.' : '读一段生活，遇见一个念头，\n也留下你的故事。'}</Text><View style={s.actions}>{<CommunityButton title={english ? 'Write a page' : '写一篇'} onPress={() => setEditing(true)} primary={true} />}{<CommunityButton title={mine ? english ? 'Everyone’s pages' : '大家的创作' : english ? 'My submissions' : '我的投稿'} onPress={() => {
        setMine(value => !value);
        setRows([]);
      }} />}</View>{featured && <Pressable style={s.feature} onPress={() => void open(featured)} accessibilityRole="button">{featured.images[0] ? <Image source={{
        uri: featured.images[0].thumbnail
      }} style={s.featureImage} /> : <View style={s.landscape}><View style={s.mountain} /><View style={s.mountainFar} /></View>}<View style={s.featureCopy}><Text style={s.eyebrow}>{english ? 'A PAGE TO LINGER ON' : '停下来，读一篇'}</Text><Text style={s.postTitle}>{featured.title}</Text><Text numberOfLines={3} style={s.excerpt}>{featured.text}</Text><Text style={s.small}>{featured.author} · {english ? 'Read →' : '展开阅读 →'}</Text></View></Pressable>}<Text style={s.listHeading}>{mine ? english ? 'My submissions' : '我的投稿' : english ? 'Everyone’s pages' : '大家的创作'}</Text><View style={s.filters}>{categories.map(key => <Pressable key={key} onPress={() => {
        setFilter(key);
        setRows([]);
      }} accessibilityRole="button" style={[s.chip, filter === key && s.selectedChip]}><Text style={s.chipText}>{label(key)}</Text></Pressable>)}</View>{rows.map(post => <Pressable key={post.id} accessibilityRole="button" style={s.row} onPress={() => void open(post)}><View style={s.byline}><View style={s.avatar}><Text style={s.avatarText}>{Array.from(post.author)[0]}</Text></View><Text style={s.small}>{post.author}</Text><Text style={[s.small, {
          marginLeft: 'auto'
        }]}>{mine ? label(post.state) : label(post.category)}</Text></View><Text style={s.postTitle}>{post.title}</Text><Text numberOfLines={3} style={s.excerpt}>{post.text}</Text>{post.images[0] && <Image source={{
        uri: post.images[0].thumbnail
      }} style={s.rowImage} />}<Text style={s.read}>{english ? 'Read the whole page →' : '展开阅读 →'}</Text></Pressable>)}{!rows.length && !busy && <View style={s.empty}><Text style={s.emptyMark}>“</Text><Text style={s.postTitle}>{english ? 'A small moment can become a story.' : '一个小小的片刻，\n也可以成为故事。'}</Text><Text style={s.note}>{mine ? english ? 'Your submissions will appear here.' : '你的投稿会显示在这里。' : english ? 'No published pages yet. Leave the first page.' : '这里还没有公开作品，留下第一篇吧。'}</Text></View>}{busy && <Text style={s.note}>{english ? 'Loading…' : '正在展开…'}</Text>}{cursor && <CommunityButton title={english ? 'More pages' : '更多作品'} onPress={() => void load(true)} primary={false} disabled={busy} />}{!!error && <Text style={s.error}>{error}</Text>}<Text style={s.note}>{english ? 'Your notes remain personal. Pages are only made public after you submit them and they are approved.' : '「记录」只属于你。作品只有主动投稿并通过审核后才会公开。'}</Text></ScrollView>;
}
const s = StyleSheet.create({
  body: {
    padding: 24,
    paddingTop: 20,
    paddingBottom: 40
  },
  eyebrow: {
    fontSize: 10,
    letterSpacing: 2,
    color: '#879374',
    marginTop: 14,
    marginBottom: 14
  },
  heading: {
    fontFamily: 'Georgia',
    fontSize: 34,
    lineHeight: 47,
    color: '#4b6242',
    marginBottom: 16
  },
  subheading: {
    fontSize: 14,
    lineHeight: 27,
    color: '#91a17c'
  },
  actions: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
    marginVertical: 24
  },
  button: {
    minHeight: 44,
    paddingHorizontal: 18,
    paddingVertical: 12,
    borderRadius: 5,
    borderWidth: 1,
    borderColor: '#cdd5bd',
    alignItems: 'center',
    justifyContent: 'center'
  },
  primary: {
    backgroundColor: '#526b48',
    borderColor: '#526b48'
  },
  buttonText: {
    fontSize: 13,
    color: '#5b7150'
  },
  feature: {
    borderWidth: 1,
    borderColor: '#dce2d2',
    backgroundColor: '#eef1e4',
    borderRadius: 7,
    overflow: 'hidden',
    marginBottom: 28
  },
  featureCopy: {
    padding: 23
  },
  featureImage: {
    width: '100%',
    height: 150
  },
  landscape: {
    height: 130,
    backgroundColor: '#e2e7d4',
    overflow: 'hidden'
  },
  mountain: {
    position: 'absolute',
    height: 160,
    width: 260,
    borderRadius: 130,
    backgroundColor: '#bac5a5',
    bottom: -90,
    left: -20,
    transform: [{
      rotate: '-14deg'
    }]
  },
  mountainFar: {
    position: 'absolute',
    height: 145,
    width: 220,
    borderRadius: 120,
    backgroundColor: '#cbd3b6',
    bottom: -100,
    right: -25,
    transform: [{
      rotate: '20deg'
    }]
  },
  listHeading: {
    fontFamily: 'Georgia',
    fontSize: 22,
    color: '#566c49',
    marginBottom: 15
  },
  filters: {
    flexDirection: 'row',
    gap: 7,
    flexWrap: 'wrap',
    marginBottom: 8
  },
  chip: {
    paddingHorizontal: 13,
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: 'transparent'
  },
  selectedChip: {
    borderBottomColor: '#617a4b',
    backgroundColor: '#e9eddf'
  },
  chipText: {
    color: '#748664',
    fontSize: 12
  },
  row: {
    paddingVertical: 25,
    borderBottomWidth: 1,
    borderBottomColor: '#dfe3d4'
  },
  byline: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8
  },
  avatar: {
    width: 27,
    height: 27,
    borderRadius: 14,
    backgroundColor: '#e1e8d4',
    justifyContent: 'center',
    alignItems: 'center'
  },
  avatarText: {
    fontSize: 12,
    color: '#789164'
  },
  small: {
    fontSize: 11,
    color: '#849377',
    lineHeight: 20
  },
  postTitle: {
    fontFamily: 'Georgia',
    fontSize: 23,
    lineHeight: 34,
    color: '#536b46',
    marginVertical: 12
  },
  excerpt: {
    fontSize: 13,
    lineHeight: 25,
    color: '#8a997d'
  },
  read: {
    fontSize: 11,
    color: '#7c916b',
    marginTop: 17
  },
  rowImage: {
    width: 95,
    height: 78,
    marginTop: 16,
    borderRadius: 4
  },
  note: {
    fontSize: 11,
    lineHeight: 22,
    color: '#8e9a81',
    marginTop: 20
  },
  error: {
    fontSize: 13,
    color: '#9e453d',
    lineHeight: 22,
    marginTop: 14
  },
  empty: {
    paddingVertical: 35
  },
  emptyMark: {
    fontFamily: 'Georgia',
    fontSize: 70,
    color: '#b6c09c',
    height: 58
  },
  editorTop: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 4,
    padding: 15
  },
  editorBody: {
    padding: 20,
    paddingBottom: 45
  },
  paper: {
    backgroundColor: '#fbf9f0',
    borderWidth: 1,
    borderColor: '#e2dfcf',
    padding: 24,
    marginTop: 16,
    shadowColor: '#454f2f',
    shadowOffset: {
      width: 0,
      height: 6
    },
    shadowOpacity: .05,
    shadowRadius: 9
  },
  tape: {
    position: 'absolute',
    top: -8,
    alignSelf: 'center',
    width: 66,
    height: 17,
    backgroundColor: '#e0ddbd88',
    transform: [{
      rotate: '-3deg'
    }]
  },
  paperDate: {
    fontSize: 10,
    color: '#a2a88c',
    marginTop: 6,
    marginBottom: 15,
    letterSpacing: 1
  },
  titleInput: {
    fontFamily: Platform.OS === 'ios' ? 'Songti SC' : 'serif',
    fontSize: 26,
    color: '#59704e',
    padding: 0,
    lineHeight: 38,
    marginBottom: 10
  },
  textInput: {
    fontFamily: Platform.OS === 'ios' ? 'Songti SC' : 'serif',
    fontSize: 19,
    lineHeight: 36,
    color: '#697b5b',
    minHeight: 270,
    textAlignVertical: 'top',
    padding: 0,
    marginVertical: 24
  },
  imageGrid: {
    flexDirection: 'row',
    gap: 12,
    flexWrap: 'wrap',
    marginVertical: 16
  },
  draftImage: {
    width: 115,
    height: 110,
    borderRadius: 5
  },
  detailText: {
    fontFamily: Platform.OS === 'ios' ? 'Songti SC' : 'serif',
    fontSize: 19,
    lineHeight: 36,
    color: '#697b5b'
  },
  detailImages: {
    gap: 16,
    marginVertical: 20
  },
  fullImage: {
    width: '100%',
    maxHeight: 550,
    backgroundColor: '#eceee4'
  }
});

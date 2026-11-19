"""OCR 이 헷갈리는 영어 글자 고치기.

고딕체에서는 대문자 I 와 소문자 l, 대문자 O 와 숫자 0 이 거의 똑같이 생겨서
OCR 이 FileInputStream → FilelnputStream, FileOutputStream → File0utputStream 처럼 읽는다.
글자 모양으로는 구별할 수 없으니 문맥으로 고친다.

1) 단어 목록으로 고치기: 문서 본문(텍스트 레이어)의 영어 단어 + 자바 기본 이름 목록.
   OCR 단어가 목록에 없고, 헷갈리는 글자만 바꾸면 목록의 단어와 같아지면 그 단어로 바꾼다.
   (같은 꼴이 되는 단어가 목록에 둘 이상이면 어느 쪽인지 모르므로 그대로 둔다)
2) 영어 글자 사이에 낀 숫자 0 → 대문자 O  (I0Exception → IOException, dir0bj → dirObj)
"""
import re
from collections.abc import Iterable

# 영어 단어 (숫자·밑줄 포함). 3글자 이상만 다룬다 (짧은 단어는 잘못 고칠 위험이 큼).
_WORD = re.compile(r"[A-Za-z][A-Za-z0-9_]{2,}")
# 헷갈리는 글자를 한 가지 꼴로 모은다: I·l → l, O·o·0 → O
_CONFUSABLE = str.maketrans({"I": "l", "O": "o", "0": "o"})
# 영어 글자 사이의 숫자 0
_ZERO_IN_WORD = re.compile(r"(?<=[A-Za-z])0(?=[A-Za-z])")

# 본문에 안 나와도 강의 자료에 자주 나오는 자바 이름
JAVA_WORDS = """
Object String StringBuilder StringBuffer System Scanner Math Integer Long Double Float
Boolean Character Byte Short Number Thread Runnable Override Deprecated Exception
RuntimeException IOException FileNotFoundException EOFException NullPointerException
IllegalArgumentException IllegalStateException IndexOutOfBoundsException
ArrayIndexOutOfBoundsException ArithmeticException ClassNotFoundException
NumberFormatException InterruptedException UnsupportedOperationException
InputStream OutputStream FileInputStream FileOutputStream BufferedInputStream
BufferedOutputStream DataInputStream DataOutputStream ObjectInputStream ObjectOutputStream
FilterInputStream FilterOutputStream ByteArrayInputStream ByteArrayOutputStream PrintStream
Reader Writer FileReader FileWriter BufferedReader BufferedWriter InputStreamReader
OutputStreamWriter PrintWriter StringReader StringWriter File FileDescriptor RandomAccessFile
Serializable Closeable AutoCloseable Comparable Comparator Iterable Iterator Collection
List ArrayList LinkedList Vector Stack Queue Deque ArrayDeque PriorityQueue Set HashSet
TreeSet LinkedHashSet Map HashMap TreeMap LinkedHashMap Hashtable Arrays Collections
Optional Stream JFrame JPanel JButton JLabel JTextField JTextArea ActionListener ActionEvent
main args length println printf print public private protected static final void int
char boolean double float long short byte class interface extends implements import package
return new null true false this super throw throws try catch finally abstract
""".split()


def _key(word: str) -> str:
    return word.translate(_CONFUSABLE)


class OcrFixer:
    """문서 하나의 단어 목록으로 OCR 결과를 고친다."""

    def __init__(self, document_texts: Iterable[str], extra_words: Iterable[str] = JAVA_WORDS):
        self.known: set[str] = set(extra_words)
        for text in document_texts:
            self.known.update(_WORD.findall(text))
        by_key: dict[str, set[str]] = {}
        for w in self.known:
            by_key.setdefault(_key(w), set()).add(w)
        # 같은 꼴이 되는 단어가 하나뿐일 때만 고칠 수 있다
        self._fix = {k: next(iter(ws)) for k, ws in by_key.items() if len(ws) == 1}

    def fix_word(self, word: str) -> str:
        if word in self.known:
            return word
        fixed = self._fix.get(_key(word))
        if fixed is not None:
            return fixed
        # 아는 단어 뒤에 대문자로 시작하는 말이 붙은 경우: FilelnputStreamTest → FileInputStream + Test
        for i in range(len(word) - 1, 2, -1):
            if word[i].isupper() and word[i:].isalnum():
                head = self._fix.get(_key(word[:i]))
                if head is not None and head != word[:i]:
                    return head + _ZERO_IN_WORD.sub("O", word[i:])
        return _ZERO_IN_WORD.sub("O", word)

    def fix(self, text: str) -> str:
        return _WORD.sub(lambda m: self.fix_word(m.group(0)), text)

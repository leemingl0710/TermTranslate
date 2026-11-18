class ExtractionError(Exception):
    """추출 중 사용자에게 보여줄 수 있는 오류."""


class UnsupportedFormatError(ExtractionError):
    pass


class CorruptedFileError(ExtractionError):
    pass


class EncryptedFileError(ExtractionError):
    pass


class ConversionError(ExtractionError):
    pass


class InputNotFoundError(ExtractionError):
    pass

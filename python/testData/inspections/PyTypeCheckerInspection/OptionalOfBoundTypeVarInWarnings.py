from typing import Optional, TypeVar


T = TypeVar('T', int)


def expects_int_subclass_or_none(x: Optional[T]):
    pass


expects_int_subclass_or_none(<warning descr="Expected type 'T ≤: int | None', got 'str' instead">'foo'</warning>)
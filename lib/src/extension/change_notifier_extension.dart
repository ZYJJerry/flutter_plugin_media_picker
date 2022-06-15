import 'package:flutter/foundation.dart';

class ListValueNotifier<E> extends ValueNotifier<List<E>> {
  ListValueNotifier(List<E> value) : super(value);

  void add(E value) {
    if (!super.value.contains(value)) {
      super.value.add(value);
      notifyListeners();
    }
  }

  bool remove(E value) {
    bool flag = super.value.remove(value);
    if (flag) {
      notifyListeners();
    }
    return flag;
  }
}

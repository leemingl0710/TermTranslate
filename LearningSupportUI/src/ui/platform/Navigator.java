package ui.platform;

/** 화면끼리 이동할 때 쓴다. 각 화면은 MainFrame 대신 이것만 알면 된다. */
@FunctionalInterface
interface Navigator {
    void show(Page page);
}

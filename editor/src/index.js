import { basicSetup } from "codemirror";
import { EditorState } from "@codemirror/state";
import { EditorView } from "@codemirror/view";
import { undo, redo } from "@codemirror/commands";
import { openSearchPanel } from "@codemirror/search";
import { lintGutter, linter } from "@codemirror/lint";
import { syntaxTree } from "@codemirror/language";
import { html } from "@codemirror/lang-html";
import { css } from "@codemirror/lang-css";
import { javascript } from "@codemirror/lang-javascript";
import { json } from "@codemirror/lang-json";

let view = null;
let root = null;
let suppressChange = false;

function languageExtension(language) {
  switch (language) {
    case "css": return css();
    case "javascript": return javascript();
    case "json": return json();
    default: return html();
  }
}

function notifyChange(value) {
  if (window.ZhiqueEditor && typeof window.ZhiqueEditor.onChange === "function") {
    window.ZhiqueEditor.onChange(value);
  }
}

function parserDiagnostics(view) {
  const diagnostics = [];
  syntaxTree(view.state).iterate({
    enter(node) {
      if (node.type.isError) {
        diagnostics.push({
          from: node.from,
          to: Math.max(node.from + 1, node.to),
          severity: "error",
          message: "这里的语法无法解析"
        });
      }
    }
  });
  return diagnostics;
}

function createEditor(value, language, readOnly) {
  if (!root) return;
  if (view) view.destroy();
  root.replaceChildren();
  const updateListener = EditorView.updateListener.of((update) => {
    if (update.docChanged && !suppressChange) {
      notifyChange(update.state.doc.toString());
    }
  });
  view = new EditorView({
    state: EditorState.create({
      doc: value || "",
      extensions: [
        basicSetup,
        languageExtension(language),
        EditorView.lineWrapping,
        EditorState.readOnly.of(Boolean(readOnly)),
        EditorView.editable.of(!readOnly),
        linter(parserDiagnostics, { delay: 350 }),
        lintGutter(),
        updateListener
      ]
    }),
    parent: root
  });
}

window.ZhiqueCodeEditor = {
  mount(element, value, language, readOnly) {
    root = element;
    createEditor(value, language, readOnly);
  },
  setDocument(value, language, readOnly) {
    createEditor(value, language, readOnly);
  },
  setValue(value) {
    if (!view || view.state.doc.toString() === value) return;
    suppressChange = true;
    view.dispatch({ changes: { from: 0, to: view.state.doc.length, insert: value } });
    suppressChange = false;
  },
  focus() {
    view?.focus();
  },
  command(name) {
    if (!view) return false;
    const action = name === "undo" ? undo : name === "redo" ? redo : name === "find" ? openSearchPanel : null;
    if (!action) return false;
    return action(view);
  }
};

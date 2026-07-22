export function renderHandout(container, state) {
    container.replaceChildren();
    const wrapper = document.createElement('div');
    wrapper.className = 'pv-handout';
    const image = document.createElement('img');
    image.src = state.handout.fileUrl;
    image.alt = state.handout.title || '';
    wrapper.appendChild(image);
    container.appendChild(wrapper);
}

window.dmhelperRenderHandout = renderHandout;

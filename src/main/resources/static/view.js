document.addEventListener('DOMContentLoaded', () => {
    // Buttons
    const btnUpdate = document.getElementById('btnUpdate');
    const btnAdd = document.getElementById('btnAdd');
    const btnDelete = document.getElementById('btnDelete');

    // Button Events - Sending signals to ViewModel
    btnUpdate.addEventListener('click', () => {
        console.log("View: Update request sent to ViewModel");
        // appViewModel.update();
    });

    btnAdd.addEventListener('click', () => {
        console.log("View: Add request sent to ViewModel");
        // appViewModel.add();
    });

    btnDelete.addEventListener('click', () => {
        console.log("View: Delete request sent to ViewModel");
        // appViewModel.delete();
    });
});